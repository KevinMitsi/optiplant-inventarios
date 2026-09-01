package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.out.InventoryMovementRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryMovementJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.InventoryPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.InventoryMovementJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryMovementPersistenceAdapter implements InventoryMovementRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of("occurredAt", "createdAt");
    private static final String DEFAULT_SORT_FIELD = "occurredAt";

    private final InventoryMovementJpaRepository repository;
    private final InventoryPersistenceMapper mapper;

    @Override
    public InventoryMovement save(InventoryMovement movement) {
        return mapper.toDomain(repository.save(mapper.toEntity(movement)));
    }

    @Override
    public PageResult<InventoryMovement> findByInventoryId(UUID inventoryId, PageQuery pageQuery) {
        PageQuery effective = pageQuery.isSorted() ? pageQuery
                : new PageQuery(pageQuery.page(), pageQuery.size(), DEFAULT_SORT_FIELD, SortDirection.DESC);

        Page<InventoryMovementJpaEntity> page = repository.findByInventoryId(inventoryId,
                PageQueryTranslator.toPageable(effective, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
