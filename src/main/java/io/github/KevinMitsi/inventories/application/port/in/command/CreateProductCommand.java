package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.List;
import java.util.UUID;

/**
 * Instruccion de alta de un producto (HU-07).
 *
 * <p>El producto que se crea es la variante principal de su familia. Las variantes son
 * opcionales: si {@code variants} viene vacia, el producto queda solo, y se le pueden anadir
 * variantes despues.
 *
 * @param unitOfMeasureId unidad en la que se cuenta el stock de este producto. Obligatoria: sin
 *                        ella no se sabria en que se miden sus existencias. No lleva factor de
 *                        conversion, porque el producto no se traduce a ninguna otra unidad.
 * @param variants        variantes que se dan de alta junto al principal. Cada una es un
 *                        producto completo, con SKU, stock y precio propios.
 */
public record CreateProductCommand(UUID organizationId,
                                   UUID categoryId,
                                   String sku,
                                   String barcode,
                                   String name,
                                   String description,
                                   UUID unitOfMeasureId,
                                   List<Variant> variants) {

    public CreateProductCommand {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    /**
     * Variante que se crea junto al producto principal.
     *
     * @param categoryId      si es nulo, hereda la categoria del principal
     * @param unitOfMeasureId si es nulo, hereda la unidad del principal
     */
    public record Variant(String sku,
                          String barcode,
                          String name,
                          String description,
                          UUID categoryId,
                          UUID unitOfMeasureId) {
    }
}
