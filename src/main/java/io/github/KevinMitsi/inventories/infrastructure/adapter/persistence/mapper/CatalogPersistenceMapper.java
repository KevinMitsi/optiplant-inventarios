package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.ProductUnit;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.CategoryJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductUnitJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UnitOfMeasureJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

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

    /**
     * Reasocia siempre las presentaciones a través de {@code replaceUnits}: es el lado
     * propietario quien escribe la columna {@code product_id}, y sin esa reasociación
     * Hibernate insertaría filas huérfanas.
     */
    default ProductJpaEntity toEntity(Product product) {
        if (product == null) {
            return null;
        }

        ProductJpaEntity entity = ProductJpaEntity.builder()
                .id(product.getId())
                .organizationId(product.getOrganizationId())
                .categoryId(product.getCategoryId())
                .sku(product.getSku())
                .barcode(product.getBarcode())
                .name(product.getName())
                .description(product.getDescription())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();

        entity.replaceUnits(product.getUnits().stream().map(this::toUnitEntity).toList());
        return entity;
    }

    default Product toDomain(ProductJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        List<ProductUnit> units = entity.getUnits().stream().map(this::toUnitDomain).toList();

        return Product.reconstitute(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getCategoryId(),
                entity.getSku(),
                entity.getBarcode(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                units,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    default ProductUnitJpaEntity toUnitEntity(ProductUnit productUnit) {
        if (productUnit == null) {
            return null;
        }
        return ProductUnitJpaEntity.builder()
                .id(productUnit.getId())
                .unit(toEntity(productUnit.getUnit()))
                .conversionFactor(productUnit.getConversionFactor())
                .baseUnit(productUnit.isBaseUnit())
                .active(productUnit.isActive())
                .build();
    }

    default ProductUnit toUnitDomain(ProductUnitJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProductUnit.reconstitute(
                entity.getId(),
                toDomain(entity.getUnit()),
                entity.getConversionFactor(),
                entity.isBaseUnit(),
                entity.isActive());
    }
}
