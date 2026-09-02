package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de productos.
 *
 * <p>Las búsquedas de un único producto usan {@code @EntityGraph} para traer también su unidad
 * en una sola consulta. Los listados paginados no lo declaran: allí actúa el
 * {@code @BatchSize} de la entidad, que resuelve las unidades de la página con un solo
 * {@code IN}.
 */
public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID>,
                                              JpaSpecificationExecutor<ProductJpaEntity> {

    @Override
    @EntityGraph(attributePaths = "unit")
    Optional<ProductJpaEntity> findById(UUID id);

    @EntityGraph(attributePaths = "unit")
    Optional<ProductJpaEntity> findByOrganizationIdAndSku(UUID organizationId, String sku);

    @EntityGraph(attributePaths = "unit")
    List<ProductJpaEntity> findByParentProductIdOrderByNameAsc(UUID parentProductId);

    boolean existsByOrganizationIdAndSku(UUID organizationId, String sku);

    boolean existsByOrganizationIdAndBarcode(UUID organizationId, String barcode);

    @Query("""
            SELECT COUNT(p)
              FROM ProductJpaEntity p
             WHERE p.categoryId = :categoryId
               AND p.active = true
            """)
    long countActiveByCategoryId(@Param("categoryId") UUID categoryId);
}
