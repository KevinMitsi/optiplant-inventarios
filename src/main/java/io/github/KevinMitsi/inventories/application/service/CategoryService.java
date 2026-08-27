package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageCategoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryCategoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateCategoryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateCategoryCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.CategorySearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.CategoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class CategoryService implements ManageCategoryUseCase, QueryCategoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private static final String CATEGORY = "la categoría";
    private static final String ORGANIZATION = "la organización";

    private final CategoryRepositoryPort categoryRepository;
    private final OrganizationRepositoryPort organizationRepository;

    public CategoryService(CategoryRepositoryPort categoryRepository,
                           OrganizationRepositoryPort organizationRepository) {
        this.categoryRepository = categoryRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public Category createCategory(CreateCategoryCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }

        String normalizedCode = normalizeCode(command.code());
        if (categoryRepository.existsByOrganizationIdAndCode(command.organizationId(), normalizedCode)) {
            throw new DuplicateResourceException(CATEGORY, "código", normalizedCode);
        }

        Category category = Category.create(
                command.organizationId(), normalizedCode, command.name(), command.description());

        Category saved = categoryRepository.save(category);
        log.info("Categoría creada: id={}, código={}", saved.getId(), saved.getCode());
        return saved;
    }

    @Override
    public Category updateCategory(UpdateCategoryCommand command) {
        Category category = loadCategory(command.categoryId());
        category.updateDetails(command.name(), command.description());
        return categoryRepository.save(category);
    }

    @Override
    public Category deactivateCategory(UUID categoryId) {
        Category category = loadCategory(categoryId);

        if (category.isActive()) {
            requireNoActiveProducts(category);
        }

        category.deactivate();

        Category saved = categoryRepository.save(category);
        log.info("Categoría desactivada: id={}", saved.getId());
        return saved;
    }

    @Override
    public Category activateCategory(UUID categoryId) {
        Category category = loadCategory(categoryId);
        category.activate();
        return categoryRepository.save(category);
    }

    @Override
    @Transactional(readOnly = true)
    public Category getCategoryById(UUID categoryId) {
        return loadCategory(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Category> searchCategories(CategorySearchCriteria criteria, PageQuery pageQuery) {
        return categoryRepository.search(criteria, pageQuery);
    }

    /**
     * Dar de baja una categoría con productos activos los dejaría apuntando a una
     * clasificación retirada, y el catálogo perdería coherencia sin que nada lo advirtiera.
     */
    private void requireNoActiveProducts(Category category) {
        long activeProducts = categoryRepository.countActiveProducts(category.getId());

        if (activeProducts > 0) {
            throw new BusinessRuleViolationException("RF-07",
                    ("No se puede dar de baja la categoría: aún tiene %d producto(s) activo(s). "
                            + "Reclasifíquelos o deles de baja primero.").formatted(activeProducts),
                    Map.of("categoryId", String.valueOf(category.getId()),
                           "activeProducts", activeProducts));
        }
    }

    private Category loadCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(CATEGORY, categoryId));
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
