package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/** El SKU forma parte de la identidad del producto y no es modificable. */
public record UpdateProductCommand(UUID productId,
                                   UUID categoryId,
                                   String barcode,
                                   String name,
                                   String description) {
}
