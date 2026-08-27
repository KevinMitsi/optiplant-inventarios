package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Representación persistente de un usuario.
 *
 * <p>El rol es la única asociación real del agregado. Es un catálogo cerrado de tres filas
 * que siempre se necesita junto al usuario, así que se declara {@code LAZY} y se trae con
 * {@code @EntityGraph} en cada consulta: con {@code EAGER}, un listado de cien usuarios
 * podría acabar emitiendo ciento una consultas.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class UserJpaEntity extends AuditableJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /** Nulo para el administrador general, cuyo alcance es toda la organización (RN-12). */
    @Column(name = "branch_id")
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleJpaEntity role;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof UserJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** Sin el hash: este texto acaba en logs y trazas. */
    @Override
    public String toString() {
        return "UserJpaEntity[id=%s, email=%s, active=%s]".formatted(id, email, active);
    }
}
