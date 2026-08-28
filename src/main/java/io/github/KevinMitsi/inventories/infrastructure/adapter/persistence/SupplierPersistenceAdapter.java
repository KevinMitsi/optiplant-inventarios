package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.SupplierSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.SupplierRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Supplier;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SupplierJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.PurchasingPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.PurchasingSpecifications;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.SupplierJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SupplierPersistenceAdapter implements SupplierRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of("code", "name", "active", "createdAt", "updatedAt");
    private static final String DEFAULT_SORT_FIELD = "name";

    private final SupplierJpaRepository repository;
    private final PurchasingPersistenceMapper mapper;

    @Override
    public Supplier save(Supplier supplier) {
        return mapper.toDomain(repository.save(mapper.toEntity(supplier)));
    }

    @Override
    public Optional<Supplier> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
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
    public PageResult<Supplier> search(SupplierSearchCriteria criteria, PageQuery pageQuery) {
        Page<SupplierJpaEntity> page = repository.findAll(
                PurchasingSpecifications.forSuppliers(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
