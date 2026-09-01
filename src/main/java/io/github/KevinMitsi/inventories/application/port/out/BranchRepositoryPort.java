package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.BranchSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia de sucursales.
 *
 * <p>Lo define la capa de aplicación y lo implementa la de infraestructura: esa inversión
 * es lo que mantiene la dirección de las dependencias apuntando hacia adentro. El servicio
 * depende de esta interfaz, no de Spring Data ni de JPA, y por eso puede probarse con un
 * doble sin levantar contexto ni contenedor.
 *
 * <p>El vocabulario es de dominio: recibe y devuelve {@link Branch}, {@link PageQuery} y
 * {@link PageResult}, nunca entidades JPA ni {@code Pageable}. Traducir entre ambos mundos
 * es responsabilidad exclusiva del adaptador.
 */
public interface BranchRepositoryPort {

    /**
     * Guarda una sucursal, sea nueva o ya existente.
     *
     * @return la sucursal persistida, que puede diferir de la recibida si la capa de
     *         persistencia rellenó algún valor
     */
    Branch save(Branch branch);

    Optional<Branch> findById(UUID id);

    /**
     * Busca por el código de negocio dentro de una organización.
     *
     * <p>Dos organizaciones distintas pueden usar el mismo código, así que la búsqueda
     * exige ambos valores para ser determinista.
     */
    Optional<Branch> findByOrganizationIdAndCode(UUID organizationId, String code);

    /**
     * Comprueba si el código ya está en uso dentro de la organización.
     *
     * <p>Permite al servicio rechazar un alta duplicada con un mensaje que señala el campo
     * concreto, en lugar de dejar que reviente el índice único con un error opaco. El
     * índice sigue siendo la garantía real frente a dos altas simultáneas; esto es la
     * comprobación amable, no la definitiva.
     */
    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    boolean existsById(UUID id);

    /**
     * Lista sucursales aplicando filtros y paginación.
     *
     * <p>Siempre paginado y nunca con un {@code findAll} desnudo: el número de sucursales
     * crece con la organización y una respuesta sin límite acabaría degradando el tiempo
     * de respuesta y la memoria del servidor (RNF-07, RNF-08).
     */
    PageResult<Branch> search(BranchSearchCriteria criteria, PageQuery pageQuery);

    /** Número de sucursales activas de una organización. Lo consume el panel de indicadores. */
    long countActiveByOrganizationId(UUID organizationId);
}
