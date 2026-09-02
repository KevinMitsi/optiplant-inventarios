package io.github.KevinMitsi.inventories.application.port.in.result;

import io.github.KevinMitsi.inventories.domain.model.Product;

import java.util.List;

/**
 * Un producto principal junto a sus variantes.
 *
 * <p>La familia solo existe de cara al catalogo, para presentar juntas las presentaciones de
 * un mismo articulo. Cada miembro es un producto autonomo: tiene su propio stock, su propio
 * precio y sus propios movimientos, y ni el inventario ni las ventas necesitan esta agrupacion
 * para operar.
 */
public record ProductFamily(Product principal, List<Product> variants) {

    public ProductFamily {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public static ProductFamily of(Product principal) {
        return new ProductFamily(principal, List.of());
    }
}
