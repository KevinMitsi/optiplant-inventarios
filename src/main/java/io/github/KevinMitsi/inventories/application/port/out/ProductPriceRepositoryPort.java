package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.ProductPrice;

import java.util.Optional;
import java.util.UUID;

public interface ProductPriceRepositoryPort {

    ProductPrice save(ProductPrice productPrice);

    Optional<ProductPrice> findByPriceListIdAndProductIdAndProductUnitId(UUID priceListId, UUID productId,
                                                                         UUID productUnitId);
}
