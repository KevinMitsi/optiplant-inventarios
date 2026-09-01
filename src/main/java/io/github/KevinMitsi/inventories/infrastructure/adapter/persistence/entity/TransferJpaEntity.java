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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cabecera de una transferencia entre sucursales, con sus líneas (ENTITIES.md §13).
 *
 * <p>{@code carrier_id}/{@code route_id}/{@code estimated_arrival_at} se activaron en Fase 5
 * (Logística): {@code Transfer.assignLogistics} es lo único que las fija, siempre antes de
 * despachar.
 */
@Entity
@Table(name = "transfer")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class TransferJpaEntity extends AuditableJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transfer_number", nullable = false, updatable = false, length = 40)
    private String transferNumber;

    @Column(name = "origin_branch_id", nullable = false, updatable = false)
    private UUID originBranchId;

    @Column(name = "destination_branch_id", nullable = false, updatable = false)
    private UUID destinationBranchId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    @Column(name = "carrier_id")
    private UUID carrierId;

    @Column(name = "route_id")
    private UUID routeId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "estimated_arrival_at")
    private Instant estimatedArrivalAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "transfer",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @lombok.Builder.Default
    private List<TransferItemJpaEntity> items = new ArrayList<>();

    /** Mantiene el lado propietario de la asociación, que es quien escribe la columna. */
    public void replaceItems(List<TransferItemJpaEntity> newItems) {
        items.clear();
        newItems.forEach(item -> {
            item.setTransfer(this);
            items.add(item);
        });
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TransferJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
