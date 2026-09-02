package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.AddProductVariantCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.result.ProductFamily;
import io.github.KevinMitsi.inventories.domain.model.Product;

import java.util.UUID;

/** Administracion del catalogo de productos y de sus variantes (HU-07, HU-08, HU-10). */
public interface ManageProductUseCase {

    /** Da de alta el producto principal y, si vienen en el comando, sus variantes. */
    ProductFamily createProduct(CreateProductCommand command);

    Product updateProduct(UpdateProductCommand command);

    /** Cuelga una variante de un producto principal ya existente. */
    Product addVariant(AddProductVariantCommand command);

    Product deactivateProduct(UUID productId);

    Product activateProduct(UUID productId);
}
