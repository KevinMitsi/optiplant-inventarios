package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Representación persistente de una sucursal.
 *
 * <p>La organización se referencia por identificador y no con {@code @ManyToOne}: son
 * agregados distintos, y sin asociación no hay proxy que pueda disparar una consulta por
 * elemento dentro de un bucle. La clave foránea sigue declarada en la migración.
 */
@Entity
@Table(name = "branch")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class BranchJpaEntity extends AuditableJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "code", nullable = false, updatable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address_line", length = 250)
    private String addressLine;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country_code", columnDefinition = "char(2)", length = 2)
    private String countryCode;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof BranchJpaEntity entity && id != null && id.equals(entity.id);
    }

    /**
     * Constante y no derivado del identificador: así la entidad sigue localizable dentro de
     * un {@code HashSet} antes y después de persistirse.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
