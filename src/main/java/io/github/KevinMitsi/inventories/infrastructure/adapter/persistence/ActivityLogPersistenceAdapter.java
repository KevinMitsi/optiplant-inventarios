package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.ActivityLogSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.ActivityLogRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ActivityLogJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.ActivityLogPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.ActivityLogJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.ActivityLogSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActivityLogPersistenceAdapter implements ActivityLogRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("occurredAt", "username", "role", "useCase", "level");

    private static final String DEFAULT_SORT_FIELD = "occurredAt";

    private final ActivityLogJpaRepository repository;
    private final ActivityLogPersistenceMapper mapper;

    @Override
    public ActivityLog save(ActivityLog activityLog) {
        return mapper.toDomain(repository.save(mapper.toEntity(activityLog)));
    }

    @Override
    public Optional<ActivityLog> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<ActivityLog> search(ActivityLogSearchCriteria criteria, PageQuery pageQuery) {
        Page<ActivityLogJpaEntity> page = repository.findAll(
                ActivityLogSpecifications.forActivityLogs(criteria), toPageable(pageQuery));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }

    /**
     * Ordena por fecha descendente cuando no se pide otra cosa.
     *
     * <p>Es la única excepción al ascendente por omisión del resto de listados: en una
     * traza, la primera página útil es la de lo que acaba de pasar, no la del primer
     * registro escrito hace meses.
     */
    private Pageable toPageable(PageQuery pageQuery) {
        if (pageQuery.isSorted()) {
            return PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD);
        }
        return PageRequest.of(pageQuery.page(), pageQuery.size(),
                Sort.by(DEFAULT_SORT_FIELD).descending());
    }
}
