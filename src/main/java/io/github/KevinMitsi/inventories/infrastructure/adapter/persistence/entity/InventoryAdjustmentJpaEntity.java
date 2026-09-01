package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cabecera de un ajuste de inventario, con sus líneas (ENTITIES.md §18).
 *
 * <p>Mismo criterio que {@code ProductJpaEntity} con sus presentaciones: las líneas son parte
 * del mismo agregado, se cargan siempre juntas y se reasocian a través de
 * {@link #replaceItems(List)} porque el lado propietario de la asociación es quien escribe
 * la columna {@code adjustment_id}.
 */
@Entity
@Table(name = "inventory_adjustment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustmentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "reason", nullable = false, updatable = false, length = 250)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @OneToMany(mappedBy = "adjustment",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @Builder.Default
    private List<InventoryAdjustmentItemJpaEntity> items = new ArrayList<>();

    /** Mantiene el lado propietario de la asociación, que es quien escribe la columna. */
    public void replaceItems(List<InventoryAdjustmentItemJpaEntity> newItems) {
        items.clear();
        newItems.forEach(item -> {
            item.setAdjustment(this);
            items.add(item);
        });
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof InventoryAdjustmentJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
