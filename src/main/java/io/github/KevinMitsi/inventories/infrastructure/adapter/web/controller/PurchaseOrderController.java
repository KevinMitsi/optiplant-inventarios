package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManagePurchaseOrderUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryPurchaseOrderUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.PurchaseOrderSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrderStatus;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PurchaseOrderDtos;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Órdenes de compra: creación, confirmación, recepción y cancelación (EP-05).
 *
 * <p>Recibir mercancía es la operación central de este controlador: cada línea recibida
 * incrementa automáticamente el inventario y recalcula el costo promedio ponderado
 * (RF-21, RF-23).
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Compras", description = "Órdenes de compra a proveedores, su confirmación y su recepción.")
public class PurchaseOrderController {

    private final ManagePurchaseOrderUseCase managePurchaseOrderUseCase;
    private final QueryPurchaseOrderUseCase queryPurchaseOrderUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final PurchasingWebMapper mapper;

    @PostMapping(value = "/branches/{branchId}/purchase-orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "createPurchaseOrder", summary = "Crear una orden de compra en borrador (HU-17, HU-18)")
    public ResponseEntity<PurchaseOrderDtos.PurchaseOrderResponse> createPurchaseOrder(
            @PathVariable UUID branchId,
            @Valid @RequestBody PurchaseOrderDtos.CreatePurchaseOrderRequest request) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "crear una orden de compra");
        UUID userId = currentUserProvider.requireUserId();

        PurchaseOrder order = managePurchaseOrderUseCase.createPurchaseOrder(
                mapper.toCommand(branchId, userId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/purchase-orders/{id}")
                .buildAndExpand(order.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(order));
    }

    @GetMapping("/branches/{branchId}/purchase-orders")
    @Operation(operationId = "searchPurchaseOrders", summary = "Consultar histórico de compras (HU-20, RF-22)",
            description = "Filtra por proveedor y/o producto para analizar el comportamiento de abastecimiento.")
    public PageResponse<PurchaseOrderDtos.PurchaseOrderResponse> searchPurchaseOrders(
            @PathVariable UUID branchId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String status,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "consultar órdenes de compra");

        PurchaseOrderStatus statusFilter = status == null || status.isBlank()
                ? null : PurchaseOrderStatus.fromString(status);

        PageResult<PurchaseOrder> result = queryPurchaseOrderUseCase.searchPurchaseOrders(
                new PurchaseOrderSearchCriteria(branchId, supplierId, productId, statusFilter),
                PageQuery.of(page, size));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/purchase-orders/{purchaseOrderId}")
    @Operation(operationId = "getPurchaseOrderById", summary = "Consultar una orden de compra")
    public PurchaseOrderDtos.PurchaseOrderResponse getPurchaseOrder(@PathVariable UUID purchaseOrderId) {
        return mapper.toResponse(queryPurchaseOrderUseCase.getPurchaseOrderById(purchaseOrderId));
    }

    @PatchMapping("/purchase-orders/{purchaseOrderId}/confirmation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "confirmPurchaseOrder", summary = "Confirmar la orden con el proveedor",
            description = "A partir de aquí puede empezar a recibirse mercancía.")
    public PurchaseOrderDtos.PurchaseOrderResponse confirmPurchaseOrder(@PathVariable UUID purchaseOrderId) {
        return mapper.toResponse(managePurchaseOrderUseCase.confirmPurchaseOrder(purchaseOrderId));
    }

    @PatchMapping("/purchase-orders/{purchaseOrderId}/cancellation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "cancelPurchaseOrder", summary = "Cancelar la orden",
            description = "Solo antes de recibir cualquier mercancía.")
    public PurchaseOrderDtos.PurchaseOrderResponse cancelPurchaseOrder(@PathVariable UUID purchaseOrderId) {
        return mapper.toResponse(managePurchaseOrderUseCase.cancelPurchaseOrder(purchaseOrderId));
    }

    @PatchMapping(value = "/purchase-orders/{purchaseOrderId}/items/{itemId}/receipt",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "receivePurchaseOrderItem", summary = "Confirmar la recepción de una línea (HU-19)",
            description = "Incrementa automáticamente el inventario y recalcula el costo promedio "
                    + "ponderado del producto (RF-21, RF-23). Admite recepción parcial.")
    public PurchaseOrderDtos.PurchaseOrderResponse receiveItem(
            @PathVariable UUID purchaseOrderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody PurchaseOrderDtos.ReceivePurchaseOrderItemRequest request) {

        UUID userId = currentUserProvider.requireUserId();

        PurchaseOrder order = managePurchaseOrderUseCase.receiveItem(
                mapper.toReceiveCommand(purchaseOrderId, itemId, request, userId));

        return mapper.toResponse(order);
    }
}
