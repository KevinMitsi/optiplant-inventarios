package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP de productos y sus presentaciones. */
public final class ProductDtos {

    private ProductDtos() {
    }

    @Schema(name = "CreateProductRequest", description = "Datos para dar de alta un producto.")
    public record CreateProductRequest(

            @Schema(description = "SKU único dentro de la organización. Se normaliza a mayúsculas "
                    + "y es inmutable una vez creado.",
                    example = "BEB-AGUA-600", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El SKU del producto es obligatorio.")
            @Size(max = 60, message = "El SKU no puede superar {max} caracteres.")
            String sku,

            @Schema(description = "Nombre comercial.", example = "Agua mineral 600 ml",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El nombre del producto es obligatorio.")
            @Size(max = 180, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Schema(description = "Categoría en la que se clasifica. Opcional.")
            UUID categoryId,

            @Schema(description = "Código de barras. Si se informa, debe ser único en la organización.",
                    example = "7701234567890")
            @Size(max = 100, message = "El código de barras no puede superar {max} caracteres.")
            String barcode,

            @Schema(description = "Descripción larga.")
            String description,

            @Schema(description = """
                    Unidad en la que se mide el stock de este producto. Obligatoria: sin ella \
                    el producto no podría recibir existencias, porque no habría forma de saber \
                    en qué se cuentan. Su factor de conversión es 1 por definición.""",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La unidad base es obligatoria.")
            UUID baseUnitId
    ) {
    }

    @Schema(name = "UpdateProductRequest", description = "Datos modificables de un producto.")
    public record UpdateProductRequest(

            @Schema(description = "Nombre comercial.", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El nombre del producto es obligatorio.")
            @Size(max = 180, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Schema(description = "Categoría en la que se clasifica.")
            UUID categoryId,

            @Schema(description = "Código de barras.")
            @Size(max = 100, message = "El código de barras no puede superar {max} caracteres.")
            String barcode,

            @Schema(description = "Descripción larga.")
            String description
    ) {
    }

    @Schema(name = "AddProductUnitRequest",
            description = "Añade una presentación adicional al producto (RF-09).")
    public record AddProductUnitRequest(

            @Schema(description = "Unidad de medida de la presentación.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La unidad de medida es obligatoria.")
            UUID unitOfMeasureId,

            @Schema(description = """
                    Cuántas unidades base equivale una de esta presentación. Si la base es la \
                    botella, una caja de 24 tiene factor 24.""",
                    example = "24", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El factor de conversión es obligatorio.")
            @DecimalMin(value = "0.000001", message = "El factor de conversión debe ser mayor que cero.")
            @Digits(integer = 12, fraction = 6,
                    message = "El factor admite hasta {integer} enteros y {fraction} decimales.")
            BigDecimal conversionFactor
    ) {
    }

    @Schema(name = "ChangeUnitFactorRequest", description = "Nuevo factor de conversión de una presentación.")
    public record ChangeUnitFactorRequest(

            @Schema(description = "Nuevo factor de conversión.", example = "12",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El factor de conversión es obligatorio.")
            @DecimalMin(value = "0.000001", message = "El factor de conversión debe ser mayor que cero.")
            @Digits(integer = 12, fraction = 6,
                    message = "El factor admite hasta {integer} enteros y {fraction} decimales.")
            BigDecimal conversionFactor
    ) {
    }

    @Schema(name = "ChangeBaseUnitRequest", description = "Designa otra presentación como unidad base.")
    public record ChangeBaseUnitRequest(

            @Schema(description = "Presentación que pasa a ser la unidad base.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La nueva unidad base es obligatoria.")
            UUID newBaseProductUnitId,

            @Schema(description = """
                    Factor que pasa a tener la unidad base anterior. Se exige explícitamente \
                    porque su equivalencia con la nueva base no es deducible: si la base pasa \
                    de botella a caja de 24, la botella pasa a valer 1/24, un dato que solo \
                    conoce el negocio.""",
                    example = "0.041667", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El nuevo factor de la unidad base anterior es obligatorio.")
            @DecimalMin(value = "0.000001", message = "El factor de conversión debe ser mayor que cero.")
            @Digits(integer = 12, fraction = 6,
                    message = "El factor admite hasta {integer} enteros y {fraction} decimales.")
            BigDecimal previousBaseNewFactor
    ) {
    }

    @Schema(name = "ProductUnitResponse", description = "Presentación en la que se maneja un producto.")
    public record ProductUnitResponse(
            @Schema(description = "Identificador de la presentación.") UUID id,
            @Schema(description = "Unidad de medida.") UnitOfMeasureResponse unit,
            @Schema(description = "Unidades base que equivale una de esta presentación.",
                    example = "24") BigDecimal conversionFactor,
            @Schema(description = "Indica si es la unidad en la que se mide el stock.",
                    example = "false") boolean baseUnit,
            @Schema(description = "Indica si la presentación sigue en uso.") boolean active
    ) {
    }

    @Schema(name = "UnitOfMeasureResponse", description = "Unidad de medida del catálogo global.")
    public record UnitOfMeasureResponse(
            @Schema(description = "Identificador único.") UUID id,
            @Schema(description = "Código de negocio.", example = "BOX") String code,
            @Schema(description = "Nombre.", example = "Caja") String name,
            @Schema(description = "Símbolo para mostrar.", example = "caja") String symbol
    ) {
    }

    @Schema(name = "ProductResponse", description = "Producto del catálogo con sus presentaciones.")
    public record ProductResponse(
            @Schema(description = "Identificador único.") UUID id,
            @Schema(description = "Organización a la que pertenece.") UUID organizationId,
            @Schema(description = "Categoría en la que se clasifica.") UUID categoryId,
            @Schema(description = "SKU.", example = "BEB-AGUA-600") String sku,
            @Schema(description = "Código de barras.") String barcode,
            @Schema(description = "Nombre comercial.") String name,
            @Schema(description = "Descripción larga.") String description,
            @Schema(description = "Indica si el producto admite operaciones nuevas.") boolean active,
            @Schema(description = """
                    Presentaciones del producto. Exactamente una tiene `baseUnit: true`: \
                    es la unidad en la que se contabiliza el stock.""")
            List<ProductUnitResponse> units,
            @Schema(description = "Fecha de creación, en UTC.") Instant createdAt,
            @Schema(description = "Fecha de la última modificación, en UTC.") Instant updatedAt
    ) {
    }
}
