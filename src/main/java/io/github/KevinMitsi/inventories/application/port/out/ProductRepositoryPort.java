package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepositoryPort {

    Product save(Product product);

    /** Carga el producto con sus presentaciones: el agregado nunca se devuelve incompleto. */
    Optional<Product> findById(UUID id);

    Optional<Product> findByOrganizationIdAndSku(UUID organizationId, String sku);

    boolean existsByOrganizationIdAndSku(UUID organizationId, String sku);

    boolean existsByOrganizationIdAndBarcode(UUID organizationId, String barcode);

    boolean existsById(UUID id);

    PageResult<Product> search(ProductSearchCriteria criteria, PageQuery pageQuery);
}
