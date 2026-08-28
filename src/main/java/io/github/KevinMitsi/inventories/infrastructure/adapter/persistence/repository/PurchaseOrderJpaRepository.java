package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PurchaseOrderJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderJpaRepository extends JpaRepository<PurchaseOrderJpaEntity, UUID>,
                                                     JpaSpecificationExecutor<PurchaseOrderJpaEntity> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<PurchaseOrderJpaEntity> findById(UUID id);

    boolean existsByBranchIdAndOrderNumber(UUID branchId, String orderNumber);
}
