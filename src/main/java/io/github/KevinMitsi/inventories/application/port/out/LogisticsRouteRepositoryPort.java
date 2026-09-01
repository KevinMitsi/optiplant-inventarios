package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.LogisticsRouteSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.RouteComplianceSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LogisticsRouteRepositoryPort {

    LogisticsRoute save(LogisticsRoute route);

    Optional<LogisticsRoute> findById(UUID id);

    boolean existsByOriginAndDestination(UUID originBranchId, UUID destinationBranchId);

    boolean existsById(UUID id);

    PageResult<LogisticsRoute> search(LogisticsRouteSearchCriteria criteria, PageQuery pageQuery);

    /** Cumplimiento estimado vs. real por ruta de la organización (HU-36/HU-37). */
    List<RouteComplianceSummary> getRouteCompliance(UUID organizationId);
}
