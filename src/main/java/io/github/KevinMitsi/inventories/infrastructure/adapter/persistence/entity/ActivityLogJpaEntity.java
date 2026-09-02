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

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la traza de auditoría.
 *
 * <p>No hereda de {@link AuditableJpaEntity}: no tiene {@code updated_at} porque nunca se
 * actualiza. Lleva su propio {@code occurred_at}, que es el instante del suceso registrado
 * y no el de la escritura de la fila.
 */
@Entity
@Table(name = "activity_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "username", nullable = false, updatable = false, length = 150)
    private String username;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "organization_id", updatable = false)
    private UUID organizationId;

    @Column(name = "actor_role", nullable = false, updatable = false, length = 30)
    private String role;

    @Column(name = "use_case", nullable = false, updatable = false, length = 150)
    private String useCase;

    @Column(name = "operation", nullable = false, updatable = false, length = 1000)
    private String operation;

    @Column(name = "log_level", nullable = false, updatable = false, length = 10)
    private String level;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ActivityLogJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
