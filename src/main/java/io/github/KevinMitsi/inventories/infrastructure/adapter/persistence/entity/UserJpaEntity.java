package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Representación persistente de un usuario.
 *
 * <p>Se llama {@code app_user} porque {@code user} es palabra reservada en PostgreSQL.
 *
 * <p><b>Por qué el rol sí es {@code @ManyToOne} y la organización no.</b> La regla general
 * del proyecto es referenciar por identificador entre agregados, y así se hace con
 * {@code organizationId} y {@code branchId}. El rol es la excepción, por dos motivos:
 * <ul>
 *   <li>No es un agregado con ciclo de vida propio, sino un <b>catálogo cerrado de tres
 *       filas</b> que nunca cambia en ejecución.</li>
 *   <li><b>Siempre se necesita junto al usuario.</b> Sin el rol no se puede decidir nada
 *       sobre autorización, así que cargarlo aparte solo añadiría una consulta más.</li>
 * </ul>
 *
 * <p>Se declara {@code LAZY} y se trae explícitamente con {@code @EntityGraph} en las
 * consultas del repositorio. Es preferible a {@code EAGER}: con carga ansiosa, Hibernate
 * decide por su cuenta y en un listado de cien usuarios puede acabar emitiendo una consulta
 * por cada uno —el problema N+1—. Con el grafo, la unión es una decisión explícita de cada
 * consulta y el listado se resuelve siempre en una sola.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserJpaEntity {

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

    /**
     * Hash BCrypt de la contraseña. Jamás la contraseña en claro (RNF-03).
     *
     * <p>Nunca debe incluirse en un DTO de respuesta ni en un texto de registro.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    /** Nunca incluye el hash de la contraseña: este texto acaba en logs y trazas. */
    @Override
    public String toString() {
        return "UserJpaEntity[id=%s, email=%s, active=%s]".formatted(id, email, active);
    }
}
