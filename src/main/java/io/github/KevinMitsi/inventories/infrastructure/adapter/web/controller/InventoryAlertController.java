package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryAlertUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlertStatus;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.InventoryAlertDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.InventoryWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Alertas inteligentes de reabastecimiento — la funcionalidad adicional obligatoria del
 * proyecto (§34).
 *
 * <p>Abrirlas y resolverlas automáticamente no es cosa de este controlador: ocurre dentro de
 * {@code InventoryMovementPoster}, en el mismo instante en que un movimiento deja un saldo
 * en, por debajo, o por encima de su mínimo. Aquí solo vive la consulta y el descarte manual.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Alertas de inventario",
     description = "Avisos automáticos de stock bajo o agotado (HU-16, RF-16, funcionalidad adicional §34).")
public class InventoryAlertController {

    private final ManageInventoryAlertUseCase alertUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final InventoryWebMapper mapper;

    @GetMapping("/inventory-alerts")
    @Operation(operationId = "searchInventoryAlerts", summary = "Consultar alertas",
            description = "Sin `branchId`, un ADMIN ve las de toda la organización (RN-12); "
                    + "con `branchId`, se exige poder operar sobre esa sucursal.")
    @ApiResponse(responseCode = "200", description = "Página de alertas.",
            content = @Content(schema = @Schema(implementation = PageResponse.class)))
    public PageResponse<InventoryAlertDtos.InventoryAlertResponse> searchAlerts(
            @Parameter(description = "Filtra por sucursal.")
            @RequestParam(required = false) UUID branchId,

            @Parameter(description = "Filtra por estado. Por defecto, solo las abiertas.")
            @RequestParam(defaultValue = "OPEN") String status,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size) {

        if (branchId != null) {
            currentUserProvider.requireCanOperateOnBranch(branchId, "consultar alertas de inventario");
        }

        InventoryAlertStatus statusFilter = status.isBlank() ? null : InventoryAlertStatus.fromString(status);

        PageResult<InventoryAlert> result = alertUseCase.searchAlerts(
                new InventoryAlertSearchCriteria(branchId, statusFilter), PageQuery.of(page, size));

        return PageResponse.from(result, mapper::toResponse);
    }

    @PostMapping("/inventory-alerts/{alertId}/resolution")
    @Operation(operationId = "resolveInventoryAlert", summary = "Resolver una alerta manualmente",
            description = "Normalmente la resuelve el propio sistema al ver que el stock se recuperó; "
                    + "este endpoint cubre el cierre manual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerta resuelta.",
                    content = @Content(schema = @Schema(implementation = InventoryAlertDtos.InventoryAlertResponse.class))),
            @ApiResponse(responseCode = "422", description = "La alerta ya no está abierta.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InventoryAlertDtos.InventoryAlertResponse resolveAlert(@PathVariable UUID alertId) {
        return mapper.toResponse(alertUseCase.resolveAlert(alertId));
    }

    @PostMapping("/inventory-alerts/{alertId}/dismissal")
    @Operation(operationId = "dismissInventoryAlert", summary = "Descartar una alerta",
            description = "Cierra la alerta sin que el stock haya cambiado, por ejemplo porque "
                    + "el reabastecimiento ya está en camino por otra vía.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerta descartada.",
                    content = @Content(schema = @Schema(implementation = InventoryAlertDtos.InventoryAlertResponse.class))),
            @ApiResponse(responseCode = "422", description = "La alerta ya no está abierta.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InventoryAlertDtos.InventoryAlertResponse dismissAlert(@PathVariable UUID alertId) {
        return mapper.toResponse(alertUseCase.dismissAlert(alertId));
    }
}
