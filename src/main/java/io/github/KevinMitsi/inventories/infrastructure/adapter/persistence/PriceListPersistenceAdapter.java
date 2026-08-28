package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.PriceListSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.PriceListRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PriceList;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PriceListJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.SalesPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.PriceListJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.SalesSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PriceListPersistenceAdapter implements PriceListRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of("code", "name", "active", "createdAt");
    private static final String DEFAULT_SORT_FIELD = "name";

    private final PriceListJpaRepository repository;
    private final SalesPersistenceMapper mapper;

    @Override
    public PriceList save(PriceList priceList) {
        return mapper.toDomain(repository.save(mapper.toEntity(priceList)));
    }

    @Override
    public Optional<PriceList> findById(UUID id) {
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
    public PageResult<PriceList> search(PriceListSearchCriteria criteria, PageQuery pageQuery) {
        Page<PriceListJpaEntity> page = repository.findAll(
                SalesSpecifications.forPriceLists(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
