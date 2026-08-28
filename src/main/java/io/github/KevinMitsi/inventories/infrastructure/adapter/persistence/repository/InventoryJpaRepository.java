package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, UUID>,
                                                 JpaSpecificationExecutor<InventoryJpaEntity> {

    Optional<InventoryJpaEntity> findByBranchIdAndProductId(UUID branchId, UUID productId);
}
