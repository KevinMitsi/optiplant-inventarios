package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.BranchSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.BranchJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.BranchPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.BranchJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.BranchSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Adaptador de salida que satisface {@link BranchRepositoryPort} con JPA. */
@Component
@RequiredArgsConstructor
public class BranchPersistenceAdapter implements BranchRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("code", "name", "city", "active", "createdAt", "updatedAt");

    private static final String DEFAULT_SORT_FIELD = "code";

    private final BranchJpaRepository repository;
    private final BranchPersistenceMapper mapper;

    @Override
    public Branch save(Branch branch) {
        BranchJpaEntity entity = mapper.toEntity(branch);
        BranchJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Branch> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Branch> findByOrganizationIdAndCode(UUID organizationId, String code) {
        return repository.findByOrganizationIdAndCode(organizationId, code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrganizationIdAndCode(UUID organizationId, String code) {
        return repository.existsByOrganizationIdAndCode(organizationId, code);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public PageResult<Branch> search(BranchSearchCriteria criteria, PageQuery pageQuery) {
        Page<BranchJpaEntity> page = repository.findAll(
                BranchSpecifications.from(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }

    @Override
    public long countActiveByOrganizationId(UUID organizationId) {
        return repository.countActiveByOrganizationId(organizationId);
    }
}
