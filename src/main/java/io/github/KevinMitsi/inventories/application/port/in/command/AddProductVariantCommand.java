package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/**
 * Anade una variante a un producto ya existente (HU-10).
 *
 * @param parentProductId producto principal al que se cuelga la variante. Debe ser principal:
 *                        el catalogo es de un solo nivel.
 * @param categoryId      si es nulo, hereda la categoria del principal
 * @param unitOfMeasureId si es nulo, hereda la unidad del principal
 */
public record AddProductVariantCommand(UUID parentProductId,
                                       String sku,
                                       String barcode,
                                       String name,
                                       String description,
                                       UUID categoryId,
                                       UUID unitOfMeasureId) {
}
