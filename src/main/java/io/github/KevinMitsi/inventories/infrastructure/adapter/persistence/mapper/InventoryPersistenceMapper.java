package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustment;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustmentItem;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlertStatus;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlertType;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryAdjustmentItemJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryAdjustmentJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryAlertJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryMovementJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduce inventario, movimientos, ajustes y alertas entre dominio y entidades persistentes.
 *
 * <p>No es un {@code @Mapper} de MapStruct: cada tipo de dominio se reconstruye a través de
 * un factory ({@code reconstitute}) que revalida sus invariantes, y todos los valores
 * monetarios y de cantidad necesitan envolverse en {@link Quantity}/{@link Money}
 * explícitamente. No hay ningún par de campos con la misma forma que un mapeo automático
 * pudiera generar, así que un componente de traducción explícito es más simple y más
 * honesto que una interfaz sin métodos que MapStruct pueda implementar realmente.
 */
@Component
public class InventoryPersistenceMapper {

    public InventoryJpaEntity toEntity(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        return InventoryJpaEntity.builder()
                .id(inventory.getId())
                .branchId(inventory.getBranchId())
                .productId(inventory.getProductId())
                .quantity(inventory.getQuantity().value())
                .minimumStock(inventory.getMinimumStock().value())
                .averageCost(inventory.getAverageCost().amount())
                .updatedAt(inventory.getUpdatedAt())
                .version(inventory.getVersion())
                .build();
    }

    public Inventory toDomain(InventoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Inventory.reconstitute(
                entity.getId(),
                entity.getBranchId(),
                entity.getProductId(),
                Quantity.of(entity.getQuantity()),
                Quantity.of(entity.getMinimumStock()),
                Money.of(entity.getAverageCost()),
                entity.getUpdatedAt(),
                entity.getVersion());
    }

    public InventoryMovementJpaEntity toEntity(InventoryMovement movement) {
        if (movement == null) {
            return null;
        }
        return InventoryMovementJpaEntity.builder()
                .id(movement.getId())
                .inventoryId(movement.getInventoryId())
                .movementType(movement.getMovementType().name())
                .userId(movement.getUserId())
                .quantity(movement.getQuantity().value())
                .unitCost(movement.getUnitCost() == null ? null : movement.getUnitCost().amount())
                .reason(movement.getReason())
                .purchaseOrderId(movement.getPurchaseOrderId())
                .saleId(movement.getSaleId())
                .transferId(movement.getTransferId())
                .adjustmentId(movement.getAdjustmentId())
                .occurredAt(movement.getOccurredAt())
                .createdAt(movement.getCreatedAt())
                .build();
    }

    public InventoryMovement toDomain(InventoryMovementJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return InventoryMovement.reconstitute(
                entity.getId(),
                entity.getInventoryId(),
                InventoryMovementType.fromString(entity.getMovementType()),
                entity.getUserId(),
                Quantity.of(entity.getQuantity()),
                entity.getUnitCost() == null ? null : Money.of(entity.getUnitCost()),
                entity.getReason(),
                entity.getPurchaseOrderId(),
                entity.getSaleId(),
                entity.getTransferId(),
                entity.getAdjustmentId(),
                entity.getOccurredAt(),
                entity.getCreatedAt());
    }

    public InventoryAdjustmentJpaEntity toEntity(InventoryAdjustment adjustment) {
        if (adjustment == null) {
            return null;
        }
        InventoryAdjustmentJpaEntity entity = InventoryAdjustmentJpaEntity.builder()
                .id(adjustment.getId())
                .branchId(adjustment.getBranchId())
                .createdBy(adjustment.getCreatedBy())
                .approvedBy(adjustment.getApprovedBy())
                .reason(adjustment.getReason())
                .createdAt(adjustment.getCreatedAt())
                .approvedAt(adjustment.getApprovedAt())
                .build();

        entity.replaceItems(adjustment.getItems().stream().map(this::toItemEntity).toList());
        return entity;
    }

    public InventoryAdjustment toDomain(InventoryAdjustmentJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<InventoryAdjustmentItem> items = entity.getItems().stream().map(this::toItemDomain).toList();
        return InventoryAdjustment.reconstitute(
                entity.getId(),
                entity.getBranchId(),
                entity.getCreatedBy(),
                entity.getApprovedBy(),
                entity.getReason(),
                items,
                entity.getCreatedAt(),
                entity.getApprovedAt());
    }

    public InventoryAdjustmentItemJpaEntity toItemEntity(InventoryAdjustmentItem item) {
        if (item == null) {
            return null;
        }
        return InventoryAdjustmentItemJpaEntity.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .quantityDelta(item.getQuantityDelta())
                .reason(item.getReason())
                .build();
    }

    public InventoryAdjustmentItem toItemDomain(InventoryAdjustmentItemJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return InventoryAdjustmentItem.reconstitute(
                entity.getId(), entity.getProductId(), entity.getQuantityDelta(), entity.getReason());
    }

    public InventoryAlertJpaEntity toEntity(InventoryAlert alert) {
        if (alert == null) {
            return null;
        }
        return InventoryAlertJpaEntity.builder()
                .id(alert.getId())
                .inventoryId(alert.getInventoryId())
                .alertType(alert.getAlertType().name())
                .status(alert.getStatus().name())
                .triggeredQuantity(alert.getTriggeredQuantity().value())
                .minimumStock(alert.getMinimumStock().value())
                .message(alert.getMessage())
                .createdAt(alert.getCreatedAt())
                .resolvedAt(alert.getResolvedAt())
                .build();
    }

    public InventoryAlert toDomain(InventoryAlertJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return InventoryAlert.reconstitute(
                entity.getId(),
                entity.getInventoryId(),
                InventoryAlertType.fromString(entity.getAlertType()),
                InventoryAlertStatus.fromString(entity.getStatus()),
                Quantity.of(entity.getTriggeredQuantity()),
                Quantity.of(entity.getMinimumStock()),
                entity.getMessage(),
                entity.getCreatedAt(),
                entity.getResolvedAt());
    }
}
