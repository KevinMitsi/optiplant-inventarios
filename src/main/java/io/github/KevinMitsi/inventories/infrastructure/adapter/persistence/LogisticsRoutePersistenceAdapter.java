package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.LogisticsRouteSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.LogisticsRouteRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.RouteComplianceSummary;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.LogisticsRouteJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.LogisticsPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.LogisticsRouteJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.LogisticsSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LogisticsRoutePersistenceAdapter implements LogisticsRouteRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "name", "estimatedDurationMinutes", "priority", "active", "createdAt", "updatedAt");
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    private final LogisticsRouteJpaRepository repository;
    private final LogisticsPersistenceMapper mapper;

    @Override
    public LogisticsRoute save(LogisticsRoute route) {
        return mapper.toDomain(repository.save(mapper.toEntity(route)));
    }

    @Override
    public Optional<LogisticsRoute> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOriginAndDestination(UUID originBranchId, UUID destinationBranchId) {
        return repository.existsByOriginBranchIdAndDestinationBranchId(originBranchId, destinationBranchId);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public PageResult<LogisticsRoute> search(LogisticsRouteSearchCriteria criteria, PageQuery pageQuery) {
        Page<LogisticsRouteJpaEntity> page = repository.findAll(
                LogisticsSpecifications.forLogisticsRoutes(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }

    @Override
    public java.util.List<RouteComplianceSummary> getRouteCompliance(UUID organizationId) {
        return repository.findComplianceByOrganizationId(organizationId).stream()
                .map(row -> {
                    long completed = row.getCompletedTransfers();
                    long onTime = row.getOnTimeTransfers();
                    Double onTimeRate = completed == 0 ? null : (double) onTime / completed;
                    return new RouteComplianceSummary(row.getRouteId(), row.getOriginBranchId(),
                            row.getDestinationBranchId(), row.getEstimatedDurationMinutes(), completed,
                            row.getAverageActualMinutes(), onTime, onTimeRate);
                })
                .toList();
    }
}
