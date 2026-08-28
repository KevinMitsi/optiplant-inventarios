package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila del histórico inmutable de movimientos (RN-04, RNF-12).
 *
 * <p>No hay {@code updatable = false} a nivel de tabla porque JPA no lo impone por sí solo,
 * pero el dominio {@link io.github.KevinMitsi.inventories.domain.model.InventoryMovement} no
 * ofrece ningún método de mutación tras construido: no existe camino de código que reconstruya
 * esta entidad para un {@code UPDATE}, solo para el {@code INSERT} inicial.
 */
@Entity
@Table(name = "inventory_movement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "inventory_id", nullable = false, updatable = false)
    private UUID inventoryId;

    @Column(name = "movement_type", nullable = false, updatable = false, length = 20)
    private String movementType;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit_cost", updatable = false, precision = 18, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "reason", nullable = false, updatable = false, length = 250)
    private String reason;

    @Column(name = "purchase_order_id", updatable = false)
    private UUID purchaseOrderId;

    @Column(name = "sale_id", updatable = false)
    private UUID saleId;

    @Column(name = "transfer_id", updatable = false)
    private UUID transferId;

    @Column(name = "adjustment_id", updatable = false)
    private UUID adjustmentId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof InventoryMovementJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
