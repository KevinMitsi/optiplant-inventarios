package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.ActivityLogSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de la traza de auditoría.
 *
 * <p>Solo inserta y consulta: no hay actualización ni borrado, y esa ausencia es la regla
 * de negocio, no un descuido. Una traza modificable no prueba nada (RNF-12).
 */
public interface ActivityLogRepositoryPort {

    ActivityLog save(ActivityLog activityLog);

    Optional<ActivityLog> findById(UUID id);

    PageResult<ActivityLog> search(ActivityLogSearchCriteria criteria, PageQuery pageQuery);
}
