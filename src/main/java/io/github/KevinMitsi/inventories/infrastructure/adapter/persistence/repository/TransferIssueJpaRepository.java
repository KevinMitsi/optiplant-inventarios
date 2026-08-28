package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.TransferIssueJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TransferIssueJpaRepository extends JpaRepository<TransferIssueJpaEntity, UUID> {

    List<TransferIssueJpaEntity> findByTransferItemIdIn(List<UUID> transferItemIds);

    @Query("select count(i) > 0 from TransferIssueJpaEntity i "
            + "where i.transferItemId in :transferItemIds and i.resolvedAt is null")
    boolean existsUnresolvedByTransferItemIdIn(@Param("transferItemIds") List<UUID> transferItemIds);
}
