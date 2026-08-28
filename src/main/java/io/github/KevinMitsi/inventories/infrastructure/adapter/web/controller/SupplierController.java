package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageSupplierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QuerySupplierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.SupplierSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.domain.model.Supplier;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.SupplierDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.PurchasingWebMapper;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
    @Operation(operationId = "createSupplier", summary = "Registrar un proveedor")
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
    @Operation(operationId = "searchSuppliers", summary = "Listar proveedores")
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
    public SupplierDtos.SupplierResponse getSupplier(@PathVariable UUID supplierId) {
        return mapper.toResponse(querySupplierUseCase.getSupplierById(supplierId));
    }

    @PutMapping(value = "/suppliers/{supplierId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "updateSupplier", summary = "Actualizar un proveedor")
    public SupplierDtos.SupplierResponse updateSupplier(
            @PathVariable UUID supplierId,
            @Valid @RequestBody SupplierDtos.UpdateSupplierRequest request) {

        return mapper.toResponse(manageSupplierUseCase.updateSupplier(mapper.toCommand(supplierId, request)));
    }

    @PostMapping("/suppliers/{supplierId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "deactivateSupplier", summary = "Dar de baja un proveedor")
    public SupplierDtos.SupplierResponse deactivateSupplier(@PathVariable UUID supplierId) {
        return mapper.toResponse(manageSupplierUseCase.deactivateSupplier(supplierId));
    }

    @PostMapping("/suppliers/{supplierId}/activation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "activateSupplier", summary = "Reactivar un proveedor")
    public SupplierDtos.SupplierResponse activateSupplier(@PathVariable UUID supplierId) {
        return mapper.toResponse(manageSupplierUseCase.activateSupplier(supplierId));
    }
}
