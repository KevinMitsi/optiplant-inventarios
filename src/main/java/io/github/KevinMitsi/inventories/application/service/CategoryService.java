package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageCategoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryCategoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateCategoryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateCategoryCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.CategorySearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.usecase.CategoryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class CategoryService implements ManageCategoryUseCase, QueryCategoryUseCase {

    private final CategoryUseCase useCase;

    public CategoryService(CategoryUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Category createCategory(CreateCategoryCommand command) {
        return useCase.createCategory(command);
    }

    @Override
    public Category updateCategory(UpdateCategoryCommand command) {
        return useCase.updateCategory(command);
    }

    @Override
    public Category deactivateCategory(UUID categoryId) {
        return useCase.deactivateCategory(categoryId);
    }

    @Override
    public Category activateCategory(UUID categoryId) {
        return useCase.activateCategory(categoryId);
    }

    @Override
    public Category getCategoryById(UUID categoryId) {
        return useCase.getCategoryById(categoryId);
    }

    @Override
    public PageResult<Category> searchCategories(CategorySearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchCategories(criteria, pageQuery);
    }
}
