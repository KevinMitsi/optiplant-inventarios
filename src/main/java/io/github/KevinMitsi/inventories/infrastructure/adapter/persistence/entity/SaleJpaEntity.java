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
 * Cabecera de una venta, con sus líneas (ENTITIES.md §11).
 *
 * <p>No extiende {@code AuditableJpaEntity}: la tabla {@code sale} solo tiene
 * {@code created_at}, no {@code updated_at} — una venta confirmada o cancelada no se
 * "actualiza" en el sentido habitual, cambia de estado, y ese cambio ya queda explicado por
 * los movimientos de inventario que genera.
 */
@Entity
@Table(name = "sale")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "price_list_id", updatable = false)
    private UUID priceListId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "sale_number", nullable = false, updatable = false, length = 40)
    private String saleNumber;

    @Column(name = "sale_date", nullable = false, updatable = false)
    private Instant saleDate;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "sale",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @Builder.Default
    private List<SaleItemJpaEntity> items = new ArrayList<>();

    /** Mantiene el lado propietario de la asociación, que es quien escribe la columna. */
    public void replaceItems(List<SaleItemJpaEntity> newItems) {
        items.clear();
        newItems.forEach(item -> {
            item.setSale(this);
            items.add(item);
        });
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SaleJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
