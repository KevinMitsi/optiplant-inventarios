package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.exception.ConcurrentModificationConflictException;
import io.github.KevinMitsi.inventories.application.port.in.query.InventorySearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.InventoryRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.InventoryPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.InventoryJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.InventorySpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryPersistenceAdapter implements InventoryRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of("quantity", "minimumStock", "updatedAt");
    private static final String DEFAULT_SORT_FIELD = "updatedAt";

    private final InventoryJpaRepository repository;
    private final InventoryPersistenceMapper mapper;

    @Override
    public Inventory save(Inventory inventory) {
        try {
            return mapper.toDomain(repository.save(mapper.toEntity(inventory)));
        } catch (OptimisticLockingFailureException cause) {
            throw new ConcurrentModificationConflictException("inventory", inventory.getId(), cause);
        }
    }

    @Override
    public Optional<Inventory> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Inventory> findByBranchIdAndProductId(UUID branchId, UUID productId) {
        return repository.findByBranchIdAndProductId(branchId, productId).map(mapper::toDomain);
    }

    @Override
    public PageResult<Inventory> search(InventorySearchCriteria criteria, PageQuery pageQuery) {
        Page<InventoryJpaEntity> page = repository.findAll(
                InventorySpecifications.forInventory(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
