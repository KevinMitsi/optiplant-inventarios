package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.CategoryJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UnitOfMeasureJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Traduce el catálogo entre modelo de dominio y entidades persistentes. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogPersistenceMapper {

    CategoryJpaEntity toEntity(Category category);

    default Category toDomain(CategoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Category.reconstitute(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    UnitOfMeasureJpaEntity toEntity(UnitOfMeasure unit);

    default UnitOfMeasure toDomain(UnitOfMeasureJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new UnitOfMeasure(entity.getId(), entity.getCode(), entity.getName(), entity.getSymbol());
    }

    default ProductJpaEntity toEntity(Product product) {
        if (product == null) {
            return null;
        }

        return ProductJpaEntity.builder()
                .id(product.getId())
                .organizationId(product.getOrganizationId())
                .parentProductId(product.getParentProductId())
                .categoryId(product.getCategoryId())
                .sku(product.getSku())
                .barcode(product.getBarcode())
                .name(product.getName())
                .description(product.getDescription())
                .unit(toEntity(product.getUnit()))
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    default Product toDomain(ProductJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return Product.reconstitute(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getParentProductId(),
                entity.getCategoryId(),
                entity.getSku(),
                entity.getBarcode(),
                entity.getName(),
                entity.getDescription(),
                toDomain(entity.getUnit()),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
