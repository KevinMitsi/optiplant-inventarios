package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Cabecera de una orden de compra, con sus líneas (ENTITIES.md §10). */
@Entity
@Table(name = "purchase_order")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PurchaseOrderJpaEntity extends AuditableJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "order_number", nullable = false, updatable = false, length = 40)
    private String orderNumber;

    @Column(name = "order_date", nullable = false, updatable = false)
    private LocalDate orderDate;

    @Column(name = "payment_term_days", nullable = false, updatable = false)
    private int paymentTermDays;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "purchaseOrder",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @lombok.Builder.Default
    private List<PurchaseOrderItemJpaEntity> items = new ArrayList<>();

    /** Mantiene el lado propietario de la asociación, que es quien escribe la columna. */
    public void replaceItems(List<PurchaseOrderItemJpaEntity> newItems) {
        items.clear();
        newItems.forEach(item -> {
            item.setPurchaseOrder(this);
            items.add(item);
        });
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PurchaseOrderJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
