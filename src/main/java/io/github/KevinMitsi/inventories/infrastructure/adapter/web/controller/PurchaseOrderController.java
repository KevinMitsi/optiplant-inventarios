package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManagePurchaseOrderUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryPurchaseOrderUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.PurchaseOrderSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrderStatus;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PurchaseOrderDtos;
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
    @Operation(operationId = "createPurchaseOrder", summary = "Crear una orden de compra en borrador (HU-17, HU-18)",
            description = """
                    La orden nace en borrador: todavía no mueve inventario. El número de orden es \
                    único y debe tener al menos una línea.

                    Cada línea apunta a un producto y a la cantidad en la unidad de ese producto. \
                    Una variante es un producto distinto: se pide en su propia línea.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orden creada en borrador.",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderDtos.PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido, o la orden llega sin líneas.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza, o la sucursal no es operable por el usuario.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La sucursal, el proveedor o algún producto no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El número de orden ya está en uso.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "El plazo de pago es negativo, o la orden queda sin líneas válidas.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de órdenes de compra.",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de paginación inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "La sucursal no es operable por el usuario.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "El valor de `status` no es un estado conocido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden encontrada, con sus líneas.",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderDtos.PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una orden con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PurchaseOrderDtos.PurchaseOrderResponse getPurchaseOrder(@PathVariable UUID purchaseOrderId) {
        return mapper.toResponse(queryPurchaseOrderUseCase.getPurchaseOrderById(purchaseOrderId));
    }

    @PatchMapping("/purchase-orders/{purchaseOrderId}/confirmation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "confirmPurchaseOrder", summary = "Confirmar la orden con el proveedor",
            description = "A partir de aquí puede empezar a recibirse mercancía.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden confirmada.",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderDtos.PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza la operación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una orden con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "La orden no está en un estado desde el que pueda confirmarse.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PurchaseOrderDtos.PurchaseOrderResponse confirmPurchaseOrder(@PathVariable UUID purchaseOrderId) {
        return mapper.toResponse(managePurchaseOrderUseCase.confirmPurchaseOrder(purchaseOrderId));
    }

    @PatchMapping("/purchase-orders/{purchaseOrderId}/cancellation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "cancelPurchaseOrder", summary = "Cancelar la orden",
            description = "Solo antes de recibir cualquier mercancía.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orden cancelada.",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderDtos.PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza la operación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una orden con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "La orden ya recibió mercancía, o su estado no admite cancelación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PurchaseOrderDtos.PurchaseOrderResponse cancelPurchaseOrder(@PathVariable UUID purchaseOrderId) {
        return mapper.toResponse(managePurchaseOrderUseCase.cancelPurchaseOrder(purchaseOrderId));
    }

    @PatchMapping(value = "/purchase-orders/{purchaseOrderId}/items/{itemId}/receipt",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "receivePurchaseOrderItem", summary = "Confirmar la recepción de una línea (HU-19)",
            description = "Incrementa automáticamente el inventario y recalcula el costo promedio "
                    + "ponderado del producto (RF-21, RF-23). Admite recepción parcial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recepción registrada. La respuesta trae la orden "
                    + "completa con la cantidad recibida acumulada de cada línea.",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderDtos.PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "La cantidad recibida falta o no es un número positivo.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza la operación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La orden o la línea no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "La orden no está confirmada, o lo recibido supera lo pedido en esa línea.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
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
