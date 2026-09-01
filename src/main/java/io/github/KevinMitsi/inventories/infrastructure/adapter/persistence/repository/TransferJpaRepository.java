package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.TransferJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, UUID>,
                                                JpaSpecificationExecutor<TransferJpaEntity> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<TransferJpaEntity> findById(UUID id);

    boolean existsByTransferNumber(String transferNumber);
}
