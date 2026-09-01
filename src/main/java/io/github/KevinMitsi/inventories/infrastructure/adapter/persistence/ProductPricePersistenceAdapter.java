package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.out.ProductPriceRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.ProductPrice;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.SalesPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.ProductPriceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductPricePersistenceAdapter implements ProductPriceRepositoryPort {

    private final ProductPriceJpaRepository repository;
    private final SalesPersistenceMapper mapper;

    @Override
    public ProductPrice save(ProductPrice productPrice) {
        return mapper.toDomain(repository.save(mapper.toEntity(productPrice)));
    }

    @Override
    public Optional<ProductPrice> findByPriceListIdAndProductIdAndProductUnitId(UUID priceListId, UUID productId,
                                                                                 UUID productUnitId) {
        return repository.findByPriceListIdAndProductIdAndProductUnitId(priceListId, productId, productUnitId)
                .map(mapper::toDomain);
    }
}
