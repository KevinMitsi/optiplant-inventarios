package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageLogisticsRouteUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryLogisticsRouteUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateLogisticsRouteCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateLogisticsRouteCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.LogisticsRouteSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.RouteComplianceSummary;
import io.github.KevinMitsi.inventories.domain.usecase.LogisticsRouteUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Primary
@Service
@Transactional(rollbackFor = Exception.class)
public class LogisticsRouteService implements ManageLogisticsRouteUseCase, QueryLogisticsRouteUseCase {

    private final LogisticsRouteUseCase useCase;

    public LogisticsRouteService(LogisticsRouteUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public LogisticsRoute createRoute(CreateLogisticsRouteCommand command) {
        return useCase.createRoute(command);
    }

    @Override
    public LogisticsRoute updateRoute(UpdateLogisticsRouteCommand command) {
        return useCase.updateRoute(command);
    }

    @Override
    public LogisticsRoute deactivateRoute(UUID routeId) {
        return useCase.deactivateRoute(routeId);
    }

    @Override
    public LogisticsRoute activateRoute(UUID routeId) {
        return useCase.activateRoute(routeId);
    }

    @Override
    public LogisticsRoute getRouteById(UUID routeId) {
        return useCase.getRouteById(routeId);
    }

    @Override
    public PageResult<LogisticsRoute> searchRoutes(LogisticsRouteSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchRoutes(criteria, pageQuery);
    }

    @Override
    public List<RouteComplianceSummary> getRouteCompliance(UUID organizationId) {
        return useCase.getRouteCompliance(organizationId);
    }
}
