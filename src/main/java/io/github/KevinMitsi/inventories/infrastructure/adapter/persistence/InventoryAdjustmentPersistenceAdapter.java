package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.out.InventoryAdjustmentRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustment;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.InventoryPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.InventoryAdjustmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryAdjustmentPersistenceAdapter implements InventoryAdjustmentRepositoryPort {

    private final InventoryAdjustmentJpaRepository repository;
    private final InventoryPersistenceMapper mapper;

    @Override
    public InventoryAdjustment save(InventoryAdjustment adjustment) {
        return mapper.toDomain(repository.save(mapper.toEntity(adjustment)));
    }

    @Override
    public Optional<InventoryAdjustment> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }
}
