package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;

import java.util.UUID;

/** Consulta del catalogo de productos (HU-09, RF-07). */
public interface QueryProductUseCase {

    Product getProductById(UUID productId);

    Product getProductBySku(UUID organizationId, String sku);

    PageResult<Product> searchProducts(ProductSearchCriteria criteria, PageQuery pageQuery);
}
