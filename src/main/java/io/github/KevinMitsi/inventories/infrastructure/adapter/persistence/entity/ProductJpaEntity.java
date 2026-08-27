package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representación persistente de un producto con sus presentaciones.
 *
 * <p>Las presentaciones sí son una asociación real, a diferencia de la categoría o la
 * organización: pertenecen al mismo agregado y el invariante de la unidad base no puede
 * comprobarse sin ellas.
 *
 * <p>{@code @BatchSize} y no {@code @EntityGraph} en los listados paginados. Traer una
 * colección con {@code JOIN FETCH} y paginar a la vez obliga a Hibernate a leer todas las
 * filas y recortar en memoria, algo que la configuración rechaza de forma explícita
 * ({@code fail_on_pagination_over_collection_fetch}). Con lotes, la página se resuelve en
 * una consulta y las presentaciones de esos productos en una segunda con {@code IN}: dos
 * consultas fijas en lugar de una por producto.
 */
@Entity
@Table(name = "product")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ProductJpaEntity extends AuditableJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "sku", nullable = false, updatable = false, length = 60)
    private String sku;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "name", nullable = false, length = 180)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "product",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @Builder.Default
    private List<ProductUnitJpaEntity> units = new ArrayList<>();

    /** Mantiene el lado propietario de la asociación, que es quien escribe la columna. */
    public void replaceUnits(List<ProductUnitJpaEntity> newUnits) {
        units.clear();
        newUnits.forEach(unit -> {
            unit.setProduct(this);
            units.add(unit);
        });
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ProductJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
