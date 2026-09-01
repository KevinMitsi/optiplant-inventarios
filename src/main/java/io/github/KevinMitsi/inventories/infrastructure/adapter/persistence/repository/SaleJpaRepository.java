package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SaleJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SaleJpaRepository extends JpaRepository<SaleJpaEntity, UUID>,
                                            JpaSpecificationExecutor<SaleJpaEntity> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<SaleJpaEntity> findById(UUID id);

    boolean existsByBranchIdAndSaleNumber(UUID branchId, String saleNumber);
}
