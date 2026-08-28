package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.TransferSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.TransferRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Transfer;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.TransferJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.TransferPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.TransferJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.TransferSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferPersistenceAdapter implements TransferRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of("transferNumber", "requestedAt", "status", "createdAt");
    private static final String DEFAULT_SORT_FIELD = "requestedAt";

    private final TransferJpaRepository repository;
    private final TransferPersistenceMapper mapper;

    @Override
    public Transfer save(Transfer transfer) {
        return mapper.toDomain(repository.save(mapper.toEntity(transfer)));
    }

    @Override
    public Optional<Transfer> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByTransferNumber(String transferNumber) {
        return repository.existsByTransferNumber(transferNumber);
    }

    @Override
    public PageResult<Transfer> search(TransferSearchCriteria criteria, PageQuery pageQuery) {
        Page<TransferJpaEntity> page = repository.findAll(
                TransferSpecifications.forTransfers(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
