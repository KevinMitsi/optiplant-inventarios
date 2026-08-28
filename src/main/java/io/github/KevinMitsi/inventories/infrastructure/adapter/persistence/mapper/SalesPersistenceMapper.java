package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.Percentage;
import io.github.KevinMitsi.inventories.domain.model.PriceList;
import io.github.KevinMitsi.inventories.domain.model.ProductPrice;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.Sale;
import io.github.KevinMitsi.inventories.domain.model.SaleItem;
import io.github.KevinMitsi.inventories.domain.model.SaleStatus;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PriceListJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductPriceJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SaleItemJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SaleJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/** Traduce listas de precios, precios y ventas entre dominio y entidades persistentes. */
@Component
public class SalesPersistenceMapper {

    public PriceListJpaEntity toEntity(PriceList priceList) {
        if (priceList == null) {
            return null;
        }
        return PriceListJpaEntity.builder()
                .id(priceList.getId())
                .organizationId(priceList.getOrganizationId())
                .code(priceList.getCode())
                .name(priceList.getName())
                .description(priceList.getDescription())
                .active(priceList.isActive())
                .validFrom(priceList.getValidFrom())
                .validUntil(priceList.getValidUntil())
                .createdAt(priceList.getCreatedAt())
                .updatedAt(priceList.getUpdatedAt())
                .build();
    }

    public PriceList toDomain(PriceListJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return PriceList.reconstitute(entity.getId(), entity.getOrganizationId(), entity.getCode(),
                entity.getName(), entity.getDescription(), entity.isActive(), entity.getValidFrom(),
                entity.getValidUntil(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public ProductPriceJpaEntity toEntity(ProductPrice productPrice) {
        if (productPrice == null) {
            return null;
        }
        return ProductPriceJpaEntity.builder()
                .id(productPrice.getId())
                .priceListId(productPrice.getPriceListId())
                .productId(productPrice.getProductId())
                .productUnitId(productPrice.getProductUnitId())
                .price(productPrice.getPrice().amount())
                .build();
    }

    public ProductPrice toDomain(ProductPriceJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProductPrice.reconstitute(entity.getId(), entity.getPriceListId(), entity.getProductId(),
                entity.getProductUnitId(), Money.of(entity.getPrice()));
    }

    public SaleJpaEntity toEntity(Sale sale) {
        if (sale == null) {
            return null;
        }
        SaleJpaEntity entity = SaleJpaEntity.builder()
                .id(sale.getId())
                .branchId(sale.getBranchId())
                .createdBy(sale.getCreatedBy())
                .priceListId(sale.getPriceListId())
                .status(sale.getStatus().name())
                .saleNumber(sale.getSaleNumber())
                .saleDate(sale.getSaleDate())
                .notes(sale.getNotes())
                .createdAt(sale.getCreatedAt())
                .build();

        entity.replaceItems(sale.getItems().stream().map(this::toItemEntity).toList());
        return entity;
    }

    public Sale toDomain(SaleJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<SaleItem> items = entity.getItems().stream().map(this::toItemDomain).toList();
        return Sale.reconstitute(entity.getId(), entity.getBranchId(), entity.getCreatedBy(),
                entity.getPriceListId(), SaleStatus.fromString(entity.getStatus()), entity.getSaleNumber(),
                entity.getSaleDate(), entity.getNotes(), items, entity.getCreatedAt());
    }

    public SaleItemJpaEntity toItemEntity(SaleItem item) {
        if (item == null) {
            return null;
        }
        return SaleItemJpaEntity.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productUnitId(item.getProductUnitId())
                .quantity(item.getQuantity().value())
                .unitPrice(item.getUnitPrice().amount())
                .discountPercentage(item.getDiscountPercentage().value())
                .build();
    }

    public SaleItem toItemDomain(SaleItemJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return SaleItem.reconstitute(entity.getId(), entity.getProductId(), entity.getProductUnitId(),
                Quantity.of(entity.getQuantity()), Money.of(entity.getUnitPrice()),
                Percentage.of(entity.getDiscountPercentage()));
    }
}
