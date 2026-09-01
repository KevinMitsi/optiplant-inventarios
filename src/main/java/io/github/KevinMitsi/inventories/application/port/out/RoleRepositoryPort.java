package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para el catálogo de roles.
 *
 * <p>La tabla contiene tres filas fijas que llegan con la migración de datos de referencia.
 * El puerto existe porque el usuario necesita el identificador persistente del rol para su
 * clave foránea, y porque los metadatos legibles se muestran en la interfaz.
 */
public interface RoleRepositoryPort {

    Optional<Role> findById(UUID id);

    /** Busca el rol del catálogo que corresponde a un código. */
    Optional<Role> findByCode(RoleCode code);

    List<Role> findAll();
}
