package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.TransferStatusHistoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransferStatusHistoryJpaRepository extends JpaRepository<TransferStatusHistoryJpaEntity, UUID> {

    List<TransferStatusHistoryJpaEntity> findByTransferIdOrderByChangedAtAsc(UUID transferId);
}
