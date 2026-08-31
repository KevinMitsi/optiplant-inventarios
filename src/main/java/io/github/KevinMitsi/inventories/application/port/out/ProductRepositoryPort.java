package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepositoryPort {

    Product save(Product product);

    /**
     * Quita la marca de base a las presentaciones del producto en su propia sentencia, antes de
     * que {@link #save} intente escribir la nueva base. Evita violar {@code
     * ux_product_unit_single_base} cuando el orden de flush del ORM no coincide con el orden de
     * negocio (degradar la anterior, promover la nueva).
     */
    void clearBaseUnit(UUID productId);

    /** Carga el producto con sus presentaciones: el agregado nunca se devuelve incompleto. */
    Optional<Product> findById(UUID id);

    Optional<Product> findByOrganizationIdAndSku(UUID organizationId, String sku);

    boolean existsByOrganizationIdAndSku(UUID organizationId, String sku);

    boolean existsByOrganizationIdAndBarcode(UUID organizationId, String barcode);

    boolean existsById(UUID id);

    PageResult<Product> search(ProductSearchCriteria criteria, PageQuery pageQuery);
}
