package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductPriceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductPriceJpaRepository extends JpaRepository<ProductPriceJpaEntity, UUID> {

    Optional<ProductPriceJpaEntity> findByPriceListIdAndProductIdAndProductUnitId(
            UUID priceListId, UUID productId, UUID productUnitId);
}
