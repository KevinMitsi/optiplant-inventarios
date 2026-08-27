package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Marcas de auditoría comunes a las entidades con ciclo de vida propio.
 *
 * <p>Los callbacks son la garantía de última instancia: el modelo de dominio ya asigna estas
 * fechas, pero cualquier camino que construya la entidad sin pasar por él dejaría columnas
 * {@code NOT NULL} sin valor. {@code @PrePersist} solo rellena lo que venga nulo, de modo que
 * respeta la fecha original al reconstituir desde la base.
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder
public abstract class AuditableJpaEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuditableJpaEntity() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
