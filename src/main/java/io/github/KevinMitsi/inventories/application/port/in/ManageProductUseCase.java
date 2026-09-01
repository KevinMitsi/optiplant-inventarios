package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.AddProductUnitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ChangeBaseUnitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ChangeProductUnitFactorCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateProductCommand;
import io.github.KevinMitsi.inventories.domain.model.Product;

import java.util.UUID;

/** Administracion del catalogo de productos y de sus presentaciones (HU-07, HU-08, HU-10). */
public interface ManageProductUseCase {

    Product createProduct(CreateProductCommand command);

    Product updateProduct(UpdateProductCommand command);

    Product addUnit(AddProductUnitCommand command);

    Product changeUnitFactor(ChangeProductUnitFactorCommand command);

    Product changeBaseUnit(ChangeBaseUnitCommand command);

    Product deactivateUnit(UUID productId, UUID productUnitId);

    Product activateUnit(UUID productId, UUID productUnitId);

    Product deactivateProduct(UUID productId);

    Product activateProduct(UUID productId);
}
