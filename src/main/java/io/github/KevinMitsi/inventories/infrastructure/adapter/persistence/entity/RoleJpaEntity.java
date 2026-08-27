package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Fila del catálogo de roles.
 *
 * <p>Se llama {@code app_role} y no {@code role} porque esta última es palabra reservada en
 * PostgreSQL y obligaría a entrecomillar el identificador en cada consulta.
 *
 * <p>Contiene las tres filas que instala la migración de datos de referencia. Las reglas de
 * alcance asociadas a cada rol no están aquí sino en el enum {@code RoleCode} del dominio:
 * una fila nueva en esta tabla no vendría acompañada de la lógica que la interpretase.
 */
@Entity
@Table(name = "app_role")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RoleJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Corresponde a un valor de {@code RoleCode}: ADMIN, BRANCH_MANAGER o INVENTORY_OPERATOR. */
    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 250)
    private String description;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RoleJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
