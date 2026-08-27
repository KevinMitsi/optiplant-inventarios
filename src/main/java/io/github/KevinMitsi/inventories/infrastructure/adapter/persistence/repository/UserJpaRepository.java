package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UserJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de Spring Data para usuarios.
 *
 * <p><b>Todas las consultas que devuelven usuarios llevan {@code @EntityGraph} sobre
 * {@code role}.</b> Sin él, la asociación perezosa se resolvería al pedir el rol de cada
 * usuario, ya fuera del alcance de la consulta original: una petición para la lista y una
 * más por cada elemento. Es el problema N+1, y en un listado de cien usuarios significa
 * ciento una consultas en lugar de una.
 *
 * <p>Con el grafo declarado, Hibernate emite una única sentencia con la unión resuelta. La
 * diferencia es medible desde el primer listado y crece de forma lineal con el número de
 * usuarios.
 */
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID>,
                                           JpaSpecificationExecutor<UserJpaEntity> {

    @Override
    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findById(UUID id);

    /**
     * Busca por correo para autenticar.
     *
     * <p>Trae el rol en la misma consulta porque el token que se emite a continuación lo
     * necesita para incorporarlo a sus datos.
     */
    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByEmail(String email);

    @EntityGraph(attributePaths = "role")
    Optional<UserJpaEntity> findByOrganizationIdAndEmail(UUID organizationId, String email);

    boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);

    /**
     * Listado paginado con filtros, resolviendo el rol en la misma consulta.
     *
     * <p>Se redefine el método de {@link JpaSpecificationExecutor} únicamente para poder
     * asociarle el grafo: la versión heredada no admite anotarlo, y sin ella el listado
     * volvería a caer en el N+1 sobre el rol.
     */
    @Override
    @EntityGraph(attributePaths = "role")
    Page<UserJpaEntity> findAll(Specification<UserJpaEntity> specification, Pageable pageable);

    /**
     * Cuenta los administradores activos de una organización.
     *
     * <p>Sostiene la regla que impide dejar la organización sin ningún administrador. Se
     * resuelve como agregación en la base: traer las filas para contarlas en memoria sería
     * trabajo desperdiciado, ya que el resultado es un número.
     */
    @Query("""
            SELECT COUNT(u)
              FROM UserJpaEntity u
             WHERE u.organizationId = :organizationId
               AND u.active = true
               AND u.role.code = 'ADMIN'
            """)
    long countActiveAdmins(@Param("organizationId") UUID organizationId);
}
