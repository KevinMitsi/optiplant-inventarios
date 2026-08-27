package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.CategorySearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.CategoryRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.CategoryJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.CatalogPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.CatalogSpecifications;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.CategoryJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CategoryPersistenceAdapter implements CategoryRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("code", "name", "active", "createdAt", "updatedAt");

    private static final String DEFAULT_SORT_FIELD = "name";

    private final CategoryJpaRepository repository;
    private final ProductJpaRepository productRepository;
    private final CatalogPersistenceMapper mapper;

    @Override
    public Category save(Category category) {
        return mapper.toDomain(repository.save(mapper.toEntity(category)));
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Category> findByOrganizationIdAndCode(UUID organizationId, String code) {
        return repository.findByOrganizationIdAndCode(organizationId, code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrganizationIdAndCode(UUID organizationId, String code) {
        return repository.existsByOrganizationIdAndCode(organizationId, code);
    }

    @Override
    public PageResult<Category> search(CategorySearchCriteria criteria, PageQuery pageQuery) {
        Page<CategoryJpaEntity> page = repository.findAll(
                CatalogSpecifications.forCategories(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }

    @Override
    public long countActiveProducts(UUID categoryId) {
        return productRepository.countActiveByCategoryId(categoryId);
    }
}
