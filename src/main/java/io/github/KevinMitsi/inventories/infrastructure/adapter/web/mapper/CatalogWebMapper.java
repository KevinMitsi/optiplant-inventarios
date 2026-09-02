package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.application.port.in.command.AddProductVariantCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateCategoryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateCategoryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.result.ProductFamily;
import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CategoryDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ProductDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogWebMapper {

    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "code", source = "request.code")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "description", source = "request.description")
    CreateCategoryCommand toCommand(UUID organizationId, CategoryDtos.CreateCategoryRequest request);

    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "description", source = "request.description")
    UpdateCategoryCommand toCommand(UUID categoryId, CategoryDtos.UpdateCategoryRequest request);

    CategoryDtos.CategoryResponse toResponse(Category category);

    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "categoryId", source = "request.categoryId")
    @Mapping(target = "sku", source = "request.sku")
    @Mapping(target = "barcode", source = "request.barcode")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "unitOfMeasureId", source = "request.unitOfMeasureId")
    @Mapping(target = "variants", source = "request.variants")
    CreateProductCommand toCommand(UUID organizationId, ProductDtos.CreateProductRequest request);

    CreateProductCommand.Variant toVariant(ProductDtos.ProductVariantRequest request);

    @Mapping(target = "productId", source = "productId")
    @Mapping(target = "categoryId", source = "request.categoryId")
    @Mapping(target = "barcode", source = "request.barcode")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "description", source = "request.description")
    UpdateProductCommand toCommand(UUID productId, ProductDtos.UpdateProductRequest request);

    @Mapping(target = "parentProductId", source = "parentProductId")
    @Mapping(target = "sku", source = "request.sku")
    @Mapping(target = "barcode", source = "request.barcode")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "categoryId", source = "request.categoryId")
    @Mapping(target = "unitOfMeasureId", source = "request.unitOfMeasureId")
    AddProductVariantCommand toCommand(UUID parentProductId, ProductDtos.ProductVariantRequest request);

    ProductDtos.ProductResponse toResponse(Product product);

    List<ProductDtos.ProductResponse> toResponses(List<Product> products);

    ProductDtos.ProductFamilyResponse toResponse(ProductFamily family);

    ProductDtos.UnitOfMeasureResponse toResponse(UnitOfMeasure unit);
}
