package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageSupplierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QuerySupplierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.SupplierSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.domain.model.Supplier;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.SupplierDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.PurchasingWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/** Proveedores (RF-17): la base sobre la que se construyen las órdenes de compra. */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Proveedores", description = "Proveedores a los que se les compran productos.")
public class SupplierController {

    private final ManageSupplierUseCase manageSupplierUseCase;
    private final QuerySupplierUseCase querySupplierUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final PurchasingWebMapper mapper;

    @PostMapping(value = "/organizations/{organizationId}/suppliers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "createSupplier", summary = "Registrar un proveedor",
            description = "El código es único dentro de la organización.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Proveedor creado.",
                    content = @Content(schema = @Schema(implementation = SupplierDtos.SupplierResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza, o la organización no es la suya.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La organización no existe.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El código ya está en uso en la organización.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SupplierDtos.SupplierResponse> createSupplier(
            @PathVariable UUID organizationId,
            @Valid @RequestBody SupplierDtos.CreateSupplierRequest request) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "crear proveedores");

        Supplier supplier = manageSupplierUseCase.createSupplier(mapper.toCommand(organizationId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/suppliers/{id}")
                .buildAndExpand(supplier.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(supplier));
    }

    @GetMapping("/organizations/{organizationId}/suppliers")
    @Operation(operationId = "searchSuppliers", summary = "Listar proveedores",
            description = "Búsqueda parcial por `text` sobre código y nombre. Si se omite `active`, "
                    + "devuelve activos e inactivos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de proveedores.",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de paginación u ordenación inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "La organización no es la del usuario.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<SupplierDtos.SupplierResponse> searchSuppliers(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) Boolean active,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size,

            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar proveedores");

        PageResult<Supplier> result = querySupplierUseCase.searchSuppliers(
                new SupplierSearchCriteria(organizationId, text, active),
                PageQuery.of(page, size, sortBy, SortDirection.fromString(sortDirection)));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/suppliers/{supplierId}")
    @Operation(operationId = "getSupplierById", summary = "Consultar un proveedor")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proveedor encontrado.",
                    content = @Content(schema = @Schema(implementation = SupplierDtos.SupplierResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un proveedor con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SupplierDtos.SupplierResponse getSupplier(@PathVariable UUID supplierId) {
        return mapper.toResponse(querySupplierUseCase.getSupplierById(supplierId));
    }

    @PutMapping(value = "/suppliers/{supplierId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "updateSupplier", summary = "Actualizar un proveedor",
            description = "El código no es modificable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proveedor actualizado.",
                    content = @Content(schema = @Schema(implementation = SupplierDtos.SupplierResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza la operación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un proveedor con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SupplierDtos.SupplierResponse updateSupplier(
            @PathVariable UUID supplierId,
            @Valid @RequestBody SupplierDtos.UpdateSupplierRequest request) {

        return mapper.toResponse(manageSupplierUseCase.updateSupplier(mapper.toCommand(supplierId, request)));
    }

    @PatchMapping("/suppliers/{supplierId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "deactivateSupplier", summary = "Dar de baja un proveedor",
            description = "Baja lógica: el proveedor sigue apareciendo en las órdenes de compra "
                    + "históricas. Idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proveedor dado de baja.",
                    content = @Content(schema = @Schema(implementation = SupplierDtos.SupplierResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza la operación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un proveedor con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SupplierDtos.SupplierResponse deactivateSupplier(@PathVariable UUID supplierId) {
        return mapper.toResponse(manageSupplierUseCase.deactivateSupplier(supplierId));
    }

    @PatchMapping("/suppliers/{supplierId}/activation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "activateSupplier", summary = "Reactivar un proveedor")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proveedor reactivado.",
                    content = @Content(schema = @Schema(implementation = SupplierDtos.SupplierResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza la operación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un proveedor con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SupplierDtos.SupplierResponse activateSupplier(@PathVariable UUID supplierId) {
        return mapper.toResponse(manageSupplierUseCase.activateSupplier(supplierId));
    }
}
