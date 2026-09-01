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

import java.util.UUID;

/**
 * Representación persistente de un producto.
 *
 * <p>La unidad de medida sí es una asociación real, a diferencia de la categoría o la
 * organización: la respuesta del catálogo muestra su código y su símbolo, y resolverla por
 * separado obligaría a una consulta por producto. Es {@code LAZY}, y el {@code @BatchSize}
 * declarado en {@link UnitOfMeasureJpaEntity} hace que una página se resuelva en dos
 * consultas fijas —los productos, y sus unidades con un {@code IN}— en lugar de una por fila.
 *
 * <p>{@code parentProductId} es una columna suelta y no una asociación: el producto padre no
 * forma parte de este agregado ni se navega al cargarlo. Quien quiera la familia la pide
 * explícitamente.
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

    @Column(name = "parent_product_id", updatable = false)
    private UUID parentProductId;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false, updatable = false)
    private UnitOfMeasureJpaEntity unit;

    @Column(name = "active", nullable = false)
    private boolean active;

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
