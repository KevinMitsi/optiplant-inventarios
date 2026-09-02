package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.result.ProductFamily;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;

import java.util.List;
import java.util.UUID;

/** Consulta del catalogo de productos (HU-09, RF-07). */
public interface QueryProductUseCase {

    Product getProductById(UUID productId);

    /** Devuelve el producto con sus variantes; si el producto es una variante, va sin ellas. */
    ProductFamily getProductFamily(UUID productId);

    Product getProductBySku(UUID organizationId, String sku);

    List<Product> listVariants(UUID parentProductId);

    PageResult<Product> searchProducts(ProductSearchCriteria criteria, PageQuery pageQuery);
}
