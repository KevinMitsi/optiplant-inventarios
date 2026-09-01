package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageSaleUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QuerySaleUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.SaleSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Sale;
import io.github.KevinMitsi.inventories.domain.model.SaleStatus;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.SaleDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.SalesWebMapper;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Ventas a clientes (EP-06).
 *
 * <p>Confirmar es la operación central: descuenta inventario mediante {@code SALE_OUT},
 * validando que haya stock disponible (RN-03, HU-22).
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ventas", description = "Ventas a clientes: creación, confirmación y cancelación.")
public class SaleController {

    private final ManageSaleUseCase manageSaleUseCase;
    private final QuerySaleUseCase querySaleUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final SalesWebMapper mapper;

    @PostMapping(value = "/branches/{branchId}/sales", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "createSale", summary = "Crear una venta en borrador (HU-22)")
    public ResponseEntity<SaleDtos.SaleResponse> createSale(
            @PathVariable UUID branchId,
            @Valid @RequestBody SaleDtos.CreateSaleRequest request) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "crear una venta");
        UUID userId = currentUserProvider.requireUserId();

        Sale sale = manageSaleUseCase.createSale(mapper.toCommand(branchId, userId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/sales/{id}").buildAndExpand(sale.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(sale));
    }

    @GetMapping("/branches/{branchId}/sales")
    @Operation(operationId = "searchSales", summary = "Consultar histórico de ventas (HU-26, RF-30)")
    public PageResponse<SaleDtos.SaleResponse> searchSales(
            @PathVariable UUID branchId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "consultar ventas");

        SaleStatus statusFilter = status == null || status.isBlank() ? null : SaleStatus.fromString(status);

        PageResult<Sale> result = querySaleUseCase.searchSales(
                new SaleSearchCriteria(branchId, statusFilter, fromDate, toDate), PageQuery.of(page, size));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/sales/{saleId}")
    @Operation(operationId = "getSaleById", summary = "Consultar una venta (comprobante, HU-26)")
    public SaleDtos.SaleResponse getSale(@PathVariable UUID saleId) {
        return mapper.toResponse(querySaleUseCase.getSaleById(saleId));
    }

    @PatchMapping("/sales/{saleId}/confirmation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "confirmSale", summary = "Confirmar la venta",
            description = "Descuenta inventario mediante SALE_OUT, validando stock disponible (RN-03).")
    public SaleDtos.SaleResponse confirmSale(@PathVariable UUID saleId) {
        return mapper.toResponse(manageSaleUseCase.confirmSale(saleId));
    }

    @PatchMapping("/sales/{saleId}/cancellation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "cancelSale", summary = "Cancelar la venta",
            description = "Si estaba confirmada, restituye el inventario con un movimiento RETURN_IN "
                    + "compensatorio.")
    public SaleDtos.SaleResponse cancelSale(@PathVariable UUID saleId) {
        return mapper.toResponse(manageSaleUseCase.cancelSale(saleId));
    }
}
