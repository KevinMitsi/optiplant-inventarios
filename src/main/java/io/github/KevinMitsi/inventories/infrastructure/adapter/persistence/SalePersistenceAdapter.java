package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.SaleSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.SaleRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Sale;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SaleJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.SalesPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.SaleJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.SalesSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SalePersistenceAdapter implements SaleRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of("saleNumber", "saleDate", "status", "createdAt");
    private static final String DEFAULT_SORT_FIELD = "saleDate";

    private final SaleJpaRepository repository;
    private final SalesPersistenceMapper mapper;

    @Override
    public Sale save(Sale sale) {
        return mapper.toDomain(repository.save(mapper.toEntity(sale)));
    }

    @Override
    public Optional<Sale> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByBranchIdAndSaleNumber(UUID branchId, String saleNumber) {
        return repository.existsByBranchIdAndSaleNumber(branchId, saleNumber);
    }

    @Override
    public PageResult<Sale> search(SaleSearchCriteria criteria, PageQuery pageQuery) {
        Page<SaleJpaEntity> page = repository.findAll(
                SalesSpecifications.forSales(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
