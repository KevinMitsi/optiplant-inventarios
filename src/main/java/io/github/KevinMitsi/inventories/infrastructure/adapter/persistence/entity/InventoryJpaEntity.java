package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Saldo de un producto en una sucursal.
 *
 * <p>Referencia {@code branch_id} y {@code product_id} como columnas planas, no como
 * asociaciones JPA: {@code Inventory} es su propio agregado y no necesita cargar la sucursal
 * ni el producto completos para aplicar un movimiento (mismo criterio que
 * {@code ProductJpaEntity.categoryId}).
 *
 * <p>{@code @Version} respalda el bloqueo optimista (RNF-05): Hibernate incrementa la columna
 * en cada {@code UPDATE} y lanza {@code ObjectOptimisticLockingFailureException} si la fila
 * cambió entre la lectura y la escritura. El adaptador de persistencia la traduce a
 * {@code ConcurrentModificationConflictException}.
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@lombok.AllArgsConstructor
public class InventoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "minimum_stock", nullable = false, precision = 18, scale = 6)
    private BigDecimal minimumStock;

    @Column(name = "average_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal averageCost;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof InventoryJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
