package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.out.TransferIssueRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.TransferPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.TransferIssueJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferIssuePersistenceAdapter implements TransferIssueRepositoryPort {

    private final TransferIssueJpaRepository repository;
    private final TransferPersistenceMapper mapper;

    @Override
    public TransferIssue save(TransferIssue issue) {
        return mapper.toDomain(repository.save(mapper.toEntity(issue)));
    }

    @Override
    public Optional<TransferIssue> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TransferIssue> findByTransferItemIdIn(List<UUID> transferItemIds) {
        return repository.findByTransferItemIdIn(transferItemIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsUnresolvedByTransferItemIdIn(List<UUID> transferItemIds) {
        return repository.existsUnresolvedByTransferItemIdIn(transferItemIds);
    }
}
