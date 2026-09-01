package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de productos.
 *
 * <p>Las búsquedas de un único producto usan {@code @EntityGraph} para traer el agregado
 * completo en una consulta. Los listados paginados no lo hacen: unir una colección y paginar
 * a la vez obliga a Hibernate a recortar en memoria. Allí actúa el {@code @BatchSize}
 * declarado en la entidad.
 */
public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID>,
                                              JpaSpecificationExecutor<ProductJpaEntity> {

    @Override
    @EntityGraph(attributePaths = {"units", "units.unit"})
    Optional<ProductJpaEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"units", "units.unit"})
    Optional<ProductJpaEntity> findByOrganizationIdAndSku(UUID organizationId, String sku);

    boolean existsByOrganizationIdAndSku(UUID organizationId, String sku);

    boolean existsByOrganizationIdAndBarcode(UUID organizationId, String barcode);

    @Query("""
            SELECT COUNT(p)
              FROM ProductJpaEntity p
             WHERE p.categoryId = :categoryId
               AND p.active = true
            """)
    long countActiveByCategoryId(@Param("categoryId") UUID categoryId);

    /**
     * Quita la marca de base a las presentaciones del producto, en su propia sentencia SQL.
     *
     * <p>Se ejecuta antes del merge del agregado para evitar que Hibernate, cuyo orden de
     * flush entre entidades hijas del mismo tipo no está garantizado, intente promocionar la
     * nueva base antes de degradar la anterior y viole {@code ux_product_unit_single_base}.
     */
    @Modifying
    @Query("UPDATE ProductUnitJpaEntity pu SET pu.baseUnit = false WHERE pu.product.id = :productId AND pu.baseUnit = true")
    void clearBaseUnit(@Param("productId") UUID productId);
}
