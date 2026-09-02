package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageLogisticsRouteUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryLogisticsRouteUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateLogisticsRouteCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateLogisticsRouteCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.LogisticsRouteSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.LogisticsRouteRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.RouteComplianceSummary;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@AuditedUseCase
public class LogisticsRouteUseCase implements ManageLogisticsRouteUseCase, QueryLogisticsRouteUseCase {

    private static final Logger log = Logger.getLogger(LogisticsRouteUseCase.class.getName());

    private static final String ROUTE = "la ruta logística";
    private static final String ORGANIZATION = "la organización";
    private static final String BRANCH = "la sucursal";

    private final LogisticsRouteRepositoryPort routeRepository;
    private final OrganizationRepositoryPort organizationRepository;
    private final BranchRepositoryPort branchRepository;

    public LogisticsRouteUseCase(LogisticsRouteRepositoryPort routeRepository,
                                 OrganizationRepositoryPort organizationRepository,
                                 BranchRepositoryPort branchRepository) {
        this.routeRepository = routeRepository;
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
    }

    @Override
    public LogisticsRoute createRoute(CreateLogisticsRouteCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }
        if (!branchRepository.existsById(command.originBranchId())) {
            throw new ResourceNotFoundException(BRANCH, command.originBranchId());
        }
        if (!branchRepository.existsById(command.destinationBranchId())) {
            throw new ResourceNotFoundException(BRANCH, command.destinationBranchId());
        }
        if (routeRepository.existsByOriginAndDestination(command.originBranchId(), command.destinationBranchId())) {
            throw new DuplicateResourceException(ROUTE, "origen/destino",
                    "%s -> %s".formatted(command.originBranchId(), command.destinationBranchId()));
        }

        LogisticsRoute route = LogisticsRoute.create(command.organizationId(), command.originBranchId(),
                command.destinationBranchId(), command.name(), command.estimatedDurationMinutes(),
                toMoney(command.estimatedCost()), toPriority(command.priority()));

        LogisticsRoute saved = routeRepository.save(route);
        log.info(() -> "Ruta logística creada: id=%s, origen=%s, destino=%s"
                .formatted(saved.getId(), saved.getOriginBranchId(), saved.getDestinationBranchId()));
        return saved;
    }

    @Override
    public LogisticsRoute updateRoute(UpdateLogisticsRouteCommand command) {
        LogisticsRoute route = loadRoute(command.routeId());
        route.updateDetails(command.name(), command.estimatedDurationMinutes(), toMoney(command.estimatedCost()),
                toPriority(command.priority()));
        return routeRepository.save(route);
    }

    @Override
    public LogisticsRoute deactivateRoute(UUID routeId) {
        LogisticsRoute route = loadRoute(routeId);
        route.deactivate();
        return routeRepository.save(route);
    }

    @Override
    public LogisticsRoute activateRoute(UUID routeId) {
        LogisticsRoute route = loadRoute(routeId);
        route.activate();
        return routeRepository.save(route);
    }

    @Override
    public LogisticsRoute getRouteById(UUID routeId) {
        return loadRoute(routeId);
    }

    @Override
    public PageResult<LogisticsRoute> searchRoutes(LogisticsRouteSearchCriteria criteria, PageQuery pageQuery) {
        return routeRepository.search(criteria, pageQuery);
    }

    @Override
    public List<RouteComplianceSummary> getRouteCompliance(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException(ORGANIZATION, organizationId);
        }
        return routeRepository.getRouteCompliance(organizationId);
    }

    private LogisticsRoute loadRoute(UUID routeId) {
        return routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException(ROUTE, routeId));
    }

    private static Money toMoney(java.math.BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }

    private static short toPriority(Short priority) {
        return priority == null ? 0 : priority;
    }
}
