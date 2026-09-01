package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateCategoryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateCategoryCommand;
import io.github.KevinMitsi.inventories.domain.model.Category;

import java.util.UUID;

/** Administracion del catalogo de categorias. */
public interface ManageCategoryUseCase {

    Category createCategory(CreateCategoryCommand command);

    Category updateCategory(UpdateCategoryCommand command);

    /**
     * Da de baja la categoria.
     *
     * @throws io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException
     *         si aun tiene productos activos clasificados, que quedarian sin clasificar
     */
    Category deactivateCategory(UUID categoryId);

    Category activateCategory(UUID categoryId);
}
