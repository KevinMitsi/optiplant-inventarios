package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.Organization;

import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para la persistencia de organizaciones. */
public interface OrganizationRepositoryPort {

    Organization save(Organization organization);

    Optional<Organization> findById(UUID id);

    Optional<Organization> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Comprueba la existencia sin traer la fila completa.
     *
     * <p>Los servicios que solo necesitan validar una clave foránea usan esto en lugar de
     * {@link #findById}: evita materializar un agregado que se iba a descartar.
     */
    boolean existsById(UUID id);
}
