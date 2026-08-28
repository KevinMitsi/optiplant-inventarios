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
 * Incidencia de recepción de una línea de transferencia (ENTITIES.md §15.3).
 *
 * <p>Referencia {@code transfer_item_id} como columna plana, no como {@code @ManyToOne}: es
 * un agregado independiente de {@code Transfer} (ver {@code TransferIssue}), y las
 * consultas que necesitan cruzar hacia su transferencia lo hacen por conjunto de
 * identificadores de línea, no por una relación JPA entre agregados.
 */
@Entity
@Table(name = "transfer_issue")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferIssueJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transfer_item_id", nullable = false, updatable = false)
    private UUID transferItemId;

    @Column(name = "issue_type", nullable = false, updatable = false, length = 20)
    private String issueType;

    @Column(name = "resolution_type", length = 20)
    private String resolutionType;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "reported_by", nullable = false, updatable = false)
    private UUID reportedBy;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TransferIssueJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
