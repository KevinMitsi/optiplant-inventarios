package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryMovementJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryMovementJpaRepository extends JpaRepository<InventoryMovementJpaEntity, UUID> {

    Page<InventoryMovementJpaEntity> findByInventoryId(UUID inventoryId, Pageable pageable);
}
