package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.application.port.in.command.CreatePurchaseOrderCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateSupplierCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceivePurchaseOrderItemCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateSupplierCommand;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrderItem;
import io.github.KevinMitsi.inventories.domain.model.Supplier;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PurchaseOrderDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.SupplierDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

/** Traduce proveedores y órdenes de compra entre el contrato HTTP y la capa de aplicación. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PurchasingWebMapper {

    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "code", source = "request.code")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "taxId", source = "request.taxId")
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "phone", source = "request.phone")
    CreateSupplierCommand toCommand(UUID organizationId, SupplierDtos.CreateSupplierRequest request);

    @Mapping(target = "supplierId", source = "supplierId")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "taxId", source = "request.taxId")
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "phone", source = "request.phone")
    UpdateSupplierCommand toCommand(UUID supplierId, SupplierDtos.UpdateSupplierRequest request);

    SupplierDtos.SupplierResponse toResponse(Supplier supplier);

    default CreatePurchaseOrderCommand toCommand(UUID branchId, UUID createdBy,
                                                 PurchaseOrderDtos.CreatePurchaseOrderRequest request) {
        List<CreatePurchaseOrderCommand.Item> items = request.items().stream()
                .map(this::toItem)
                .toList();

        int paymentTermDays = request.paymentTermDays() == null ? 0 : request.paymentTermDays();

        return new CreatePurchaseOrderCommand(branchId, request.supplierId(), createdBy, request.orderNumber(),
                request.orderDate(), paymentTermDays, request.notes(), items);
    }

    default CreatePurchaseOrderCommand.Item toItem(PurchaseOrderDtos.CreatePurchaseOrderRequest.ItemRequest item) {
        return new CreatePurchaseOrderCommand.Item(item.productId(), item.productUnitId(), item.quantity(),
                item.unitPrice(), item.discountPercentage());
    }

    default ReceivePurchaseOrderItemCommand toReceiveCommand(UUID purchaseOrderId, UUID itemId,
                                                              PurchaseOrderDtos.ReceivePurchaseOrderItemRequest request,
                                                              UUID userId) {
        return new ReceivePurchaseOrderItemCommand(purchaseOrderId, itemId, request.quantityReceived(), userId);
    }

    PurchaseOrderDtos.PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder);

    PurchaseOrderDtos.PurchaseOrderResponse.ItemResponse toResponse(PurchaseOrderItem item);

    default java.math.BigDecimal map(io.github.KevinMitsi.inventories.domain.model.Quantity quantity) {
        return quantity == null ? null : quantity.value();
    }

    default java.math.BigDecimal map(io.github.KevinMitsi.inventories.domain.model.Money money) {
        return money == null ? null : money.amount();
    }

    default java.math.BigDecimal map(io.github.KevinMitsi.inventories.domain.model.Percentage percentage) {
        return percentage == null ? null : percentage.value();
    }
}
