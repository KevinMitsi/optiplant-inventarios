package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryAlertJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface InventoryAlertJpaRepository extends JpaRepository<InventoryAlertJpaEntity, UUID>,
                                                      JpaSpecificationExecutor<InventoryAlertJpaEntity> {

    Optional<InventoryAlertJpaEntity> findFirstByInventoryIdAndStatus(UUID inventoryId, String status);
}
