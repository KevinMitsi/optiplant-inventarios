package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.out.TransferStatusHistoryRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.TransferStatusHistory;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.TransferPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.TransferStatusHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferStatusHistoryPersistenceAdapter implements TransferStatusHistoryRepositoryPort {

    private final TransferStatusHistoryJpaRepository repository;
    private final TransferPersistenceMapper mapper;

    @Override
    public TransferStatusHistory save(TransferStatusHistory history) {
        return mapper.toDomain(repository.save(mapper.toEntity(history)));
    }

    @Override
    public List<TransferStatusHistory> findByTransferId(UUID transferId) {
        return repository.findByTransferIdOrderByChangedAtAsc(transferId).stream().map(mapper::toDomain).toList();
    }
}
