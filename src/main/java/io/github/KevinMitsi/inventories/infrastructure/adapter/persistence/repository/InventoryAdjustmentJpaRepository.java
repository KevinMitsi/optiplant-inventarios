package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryAdjustmentJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryAdjustmentJpaRepository extends JpaRepository<InventoryAdjustmentJpaEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<InventoryAdjustmentJpaEntity> findById(UUID id);
}
