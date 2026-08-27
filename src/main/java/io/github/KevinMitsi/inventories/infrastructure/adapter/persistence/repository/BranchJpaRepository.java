package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.BranchJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de Spring Data para sucursales.
 *
 * <p>Es un detalle de infraestructura y nada fuera de este paquete debe conocerlo. La capa
 * de aplicación habla con {@code BranchRepositoryPort}; quien conecta ambos es
 * {@code BranchPersistenceAdapter}. Ese salto de una interfaz a otra es lo que permite
 * sustituir Spring Data por otra tecnología sin tocar un solo servicio.
 *
 * <p>Extiende {@link JpaSpecificationExecutor} porque la búsqueda de sucursales combina
 * filtros opcionales. Construir la consulta con criterios evita la alternativa habitual:
 * un método derivado por cada combinación de parámetros, o una consulta con una ristra de
 * {@code (:param IS NULL OR campo = :param)} que impide a PostgreSQL elegir un buen plan.
 */
public interface BranchJpaRepository extends JpaRepository<BranchJpaEntity, UUID>,
                                             JpaSpecificationExecutor<BranchJpaEntity> {

    Optional<BranchJpaEntity> findByOrganizationIdAndCode(UUID organizationId, String code);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    /**
     * Cuenta las sucursales operativas de una organización.
     *
     * <p>Consulta de agregación explícita: resuelve el conteo en la base y devuelve un
     * número, en lugar de traer las filas para contarlas en memoria.
     */
    @Query("""
            SELECT COUNT(b)
              FROM BranchJpaEntity b
             WHERE b.organizationId = :organizationId
               AND b.active = true
            """)
    long countActiveByOrganizationId(@Param("organizationId") UUID organizationId);
}
