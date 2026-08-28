package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.Percentage;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrderItem;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrderStatus;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.Supplier;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PurchaseOrderItemJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PurchaseOrderJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SupplierJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduce proveedores y órdenes de compra entre dominio y entidades persistentes.
 *
 * <p>No es un {@code @Mapper} de MapStruct por el mismo motivo que
 * {@link InventoryPersistenceMapper}: cada agregado se reconstruye mediante un factory que
 * revalida invariantes y envuelve valores en {@link Quantity}/{@link Money}/{@link Percentage}.
 */
@Component
public class PurchasingPersistenceMapper {

    public SupplierJpaEntity toEntity(Supplier supplier) {
        if (supplier == null) {
            return null;
        }
        return SupplierJpaEntity.builder()
                .id(supplier.getId())
                .organizationId(supplier.getOrganizationId())
                .code(supplier.getCode())
                .name(supplier.getName())
                .taxId(supplier.getTaxId())
                .email(supplier.getEmail())
                .phone(supplier.getPhone())
                .active(supplier.isActive())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }

    public Supplier toDomain(SupplierJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Supplier.reconstitute(entity.getId(), entity.getOrganizationId(), entity.getCode(),
                entity.getName(), entity.getTaxId(), entity.getEmail(), entity.getPhone(), entity.isActive(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public PurchaseOrderJpaEntity toEntity(PurchaseOrder order) {
        if (order == null) {
            return null;
        }
        PurchaseOrderJpaEntity entity = PurchaseOrderJpaEntity.builder()
                .id(order.getId())
                .branchId(order.getBranchId())
                .supplierId(order.getSupplierId())
                .createdBy(order.getCreatedBy())
                .status(order.getStatus().name())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .paymentTermDays(order.getPaymentTermDays())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();

        entity.replaceItems(order.getItems().stream().map(this::toItemEntity).toList());
        return entity;
    }

    public PurchaseOrder toDomain(PurchaseOrderJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<PurchaseOrderItem> items = entity.getItems().stream().map(this::toItemDomain).toList();
        return PurchaseOrder.reconstitute(entity.getId(), entity.getBranchId(), entity.getSupplierId(),
                entity.getCreatedBy(), PurchaseOrderStatus.fromString(entity.getStatus()), entity.getOrderNumber(),
                entity.getOrderDate(), entity.getPaymentTermDays(), entity.getNotes(), items,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public PurchaseOrderItemJpaEntity toItemEntity(PurchaseOrderItem item) {
        if (item == null) {
            return null;
        }
        return PurchaseOrderItemJpaEntity.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productUnitId(item.getProductUnitId())
                .quantity(item.getQuantity().value())
                .receivedQuantity(item.getReceivedQuantity().value())
                .unitPrice(item.getUnitPrice().amount())
                .discountPercentage(item.getDiscountPercentage().value())
                .build();
    }

    public PurchaseOrderItem toItemDomain(PurchaseOrderItemJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return PurchaseOrderItem.reconstitute(entity.getId(), entity.getProductId(), entity.getProductUnitId(),
                Quantity.of(entity.getQuantity()), Quantity.of(entity.getReceivedQuantity()),
                Money.of(entity.getUnitPrice()), Percentage.of(entity.getDiscountPercentage()));
    }
}
