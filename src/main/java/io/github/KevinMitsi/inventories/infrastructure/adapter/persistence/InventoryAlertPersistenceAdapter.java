package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAlertRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlertStatus;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryAlertJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.InventoryPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.InventoryAlertJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.InventorySpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryAlertPersistenceAdapter implements InventoryAlertRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "resolvedAt");
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    private final InventoryAlertJpaRepository repository;
    private final InventoryPersistenceMapper mapper;

    @Override
    public InventoryAlert save(InventoryAlert alert) {
        return mapper.toDomain(repository.save(mapper.toEntity(alert)));
    }

    @Override
    public Optional<InventoryAlert> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<InventoryAlert> findOpenByInventoryId(UUID inventoryId) {
        return repository.findFirstByInventoryIdAndStatus(inventoryId, InventoryAlertStatus.OPEN.name())
                .map(mapper::toDomain);
    }

    @Override
    public PageResult<InventoryAlert> search(InventoryAlertSearchCriteria criteria, PageQuery pageQuery) {
        Page<InventoryAlertJpaEntity> page = repository.findAll(
                InventorySpecifications.forAlerts(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
