package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/**
 * Instruccion de alta de un producto (HU-07).
 *
 * @param baseUnitId unidad en la que se mide el stock. Obligatoria: sin ella el producto no
 *                   podria recibir existencias.
 */
public record CreateProductCommand(UUID organizationId,
                                   UUID categoryId,
                                   String sku,
                                   String barcode,
                                   String name,
                                   String description,
                                   UUID baseUnitId) {
}
