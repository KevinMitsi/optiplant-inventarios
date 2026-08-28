package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryAdjustmentUseCase;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustment;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.InventoryAdjustmentDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.InventoryWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Ajustes de inventario formales: corrección con varias líneas y aprobación (ENTITIES.md §18).
 *
 * <p>Crear el ajuste no mueve stock; solo aprobarlo lo hace, posteando un movimiento por
 * línea. Es deliberadamente un flujo de dos pasos: separa quién detecta la diferencia de
 * quién autoriza que se corrija.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ajustes de inventario", description = "Correcciones formales de stock, con responsable y aprobador.")
public class InventoryAdjustmentController {

    private final ManageInventoryAdjustmentUseCase adjustmentUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final InventoryWebMapper mapper;

    @PostMapping(value = "/branches/{branchId}/inventory-adjustments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "createInventoryAdjustment", summary = "Crear un ajuste en borrador",
            description = "Registra un ajuste con sus líneas. No mueve stock todavía.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ajuste creado.",
                    content = @Content(schema = @Schema(implementation = InventoryAdjustmentDtos.InventoryAdjustmentResponse.class))),
            @ApiResponse(responseCode = "404", description = "La sucursal o algún producto no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<InventoryAdjustmentDtos.InventoryAdjustmentResponse> createAdjustment(
            @PathVariable UUID branchId,
            @Valid @RequestBody InventoryAdjustmentDtos.CreateInventoryAdjustmentRequest request) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "crear un ajuste de inventario");
        UUID userId = currentUserProvider.requireUserId();

        InventoryAdjustment adjustment = adjustmentUseCase.createAdjustment(
                mapper.toCommand(branchId, userId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/inventory-adjustments/{id}")
                .buildAndExpand(adjustment.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(adjustment));
    }

    @GetMapping("/inventory-adjustments/{adjustmentId}")
    @Operation(operationId = "getInventoryAdjustment", summary = "Consultar un ajuste")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajuste encontrado.",
                    content = @Content(schema = @Schema(implementation = InventoryAdjustmentDtos.InventoryAdjustmentResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un ajuste con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InventoryAdjustmentDtos.InventoryAdjustmentResponse getAdjustment(@PathVariable UUID adjustmentId) {
        return mapper.toResponse(adjustmentUseCase.getAdjustmentById(adjustmentId));
    }

    @PostMapping("/inventory-adjustments/{adjustmentId}/approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "approveInventoryAdjustment", summary = "Aprobar el ajuste",
            description = "Confirma el ajuste y postea un movimiento `ADJUSTMENT_IN`/`ADJUSTMENT_OUT` "
                    + "por cada línea. A partir de aquí el documento es inmutable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajuste aprobado y stock actualizado.",
                    content = @Content(schema = @Schema(implementation = InventoryAdjustmentDtos.InventoryAdjustmentResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un ajuste con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "El ajuste ya fue aprobado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InventoryAdjustmentDtos.InventoryAdjustmentResponse approveAdjustment(@PathVariable UUID adjustmentId) {
        UUID approvedBy = currentUserProvider.requireUserId();
        return mapper.toResponse(adjustmentUseCase.approveAdjustment(adjustmentId, approvedBy));
    }
}
