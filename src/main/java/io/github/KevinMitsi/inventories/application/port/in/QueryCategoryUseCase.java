package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.CategorySearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;

public interface QueryCategoryUseCase {

    Category getCategoryById(UUID categoryId);

    PageResult<Category> searchCategories(CategorySearchCriteria criteria, PageQuery pageQuery);
}
