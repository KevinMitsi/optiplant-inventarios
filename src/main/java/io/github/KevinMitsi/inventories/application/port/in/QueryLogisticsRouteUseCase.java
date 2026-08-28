package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.LogisticsRouteSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.RouteComplianceSummary;

import java.util.List;
import java.util.UUID;

public interface QueryLogisticsRouteUseCase {

    LogisticsRoute getRouteById(UUID routeId);

    PageResult<LogisticsRoute> searchRoutes(LogisticsRouteSearchCriteria criteria, PageQuery pageQuery);

    /** Cumplimiento estimado vs. real por ruta de la organización (HU-36/HU-37). */
    List<RouteComplianceSummary> getRouteCompliance(UUID organizationId);
}
