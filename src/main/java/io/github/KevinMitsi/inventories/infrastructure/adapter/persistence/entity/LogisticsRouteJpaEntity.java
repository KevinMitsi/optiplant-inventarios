package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "logistics_route")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class LogisticsRouteJpaEntity extends AuditableJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "origin_branch_id", nullable = false, updatable = false)
    private UUID originBranchId;

    @Column(name = "destination_branch_id", nullable = false, updatable = false)
    private UUID destinationBranchId;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private int estimatedDurationMinutes;

    @Column(name = "estimated_cost", precision = 18, scale = 4)
    private BigDecimal estimatedCost;

    @Column(name = "priority", nullable = false)
    private short priority;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof LogisticsRouteJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
