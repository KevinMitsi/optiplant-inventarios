package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.CatalogPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.CatalogSpecifications;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("sku", "name", "barcode", "active", "createdAt", "updatedAt");

    private static final String DEFAULT_SORT_FIELD = "name";

    private final ProductJpaRepository repository;
    private final CatalogPersistenceMapper mapper;

    @Override
    public Product save(Product product) {
        return mapper.toDomain(repository.save(mapper.toEntity(product)));
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Product> findByOrganizationIdAndSku(UUID organizationId, String sku) {
        return repository.findByOrganizationIdAndSku(organizationId, sku).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrganizationIdAndSku(UUID organizationId, String sku) {
        return repository.existsByOrganizationIdAndSku(organizationId, sku);
    }

    @Override
    public boolean existsByOrganizationIdAndBarcode(UUID organizationId, String barcode) {
        return repository.existsByOrganizationIdAndBarcode(organizationId, barcode);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public PageResult<Product> search(ProductSearchCriteria criteria, PageQuery pageQuery) {
        Page<ProductJpaEntity> page = repository.findAll(
                CatalogSpecifications.forProducts(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
