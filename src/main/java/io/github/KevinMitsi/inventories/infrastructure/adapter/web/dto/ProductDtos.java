package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP de productos y sus variantes. */
public final class ProductDtos {

    private ProductDtos() {
    }

    @Schema(name = "CreateProductRequest",
            description = "Datos para dar de alta un producto y, opcionalmente, sus variantes.")
    public record CreateProductRequest(

            @Schema(description = "SKU único dentro de la organización. Se normaliza a mayúsculas "
                    + "y es inmutable una vez creado.",
                    example = "BEB-BRISA-BOT-1L", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El SKU del producto es obligatorio.")
            @Size(max = 60, message = "El SKU no puede superar {max} caracteres.")
            String sku,

            @Schema(description = "Nombre comercial.", example = "Agua Brisa Botella 1 L",
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
                    Unidad en la que se cuenta el stock de este producto: botella, bolsa, kg… \
                    Obligatoria, porque sin ella no se sabría en qué se miden sus existencias. \
                    No lleva factor de conversión: el stock son unidades de esta unidad, sin \
                    traducir a ninguna otra.""",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La unidad de medida es obligatoria.")
            UUID unitOfMeasureId,

            @Schema(description = """
                    Variantes que se dan de alta junto al producto. Opcional: si se omite, el \
                    producto queda solo y se le pueden añadir variantes más tarde.

                    Una variante NO es otra presentación del mismo stock: es un producto \
                    aparte, con su propio SKU, su propio inventario y su propio precio. \
                    «Agua Brisa Botella 1 L» y «Agua Brisa Bolsa x 24» se cuentan por separado \
                    y solo aparecen juntas en el catálogo.""")
            @Valid
            List<ProductVariantRequest> variants
    ) {
    }

    @Schema(name = "ProductVariantRequest",
            description = "Variante de un producto. Es un producto completo, no una presentación.")
    public record ProductVariantRequest(

            @Schema(description = "SKU propio de la variante.", example = "BEB-BRISA-BOL-24",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El SKU de la variante es obligatorio.")
            @Size(max = 60, message = "El SKU no puede superar {max} caracteres.")
            String sku,

            @Schema(description = "Nombre comercial de la variante.",
                    example = "Agua Brisa Bolsa x 24", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El nombre de la variante es obligatorio.")
            @Size(max = 180, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Schema(description = "Código de barras propio. Si se informa, debe ser único.")
            @Size(max = 100, message = "El código de barras no puede superar {max} caracteres.")
            String barcode,

            @Schema(description = "Descripción larga.")
            String description,

            @Schema(description = "Categoría propia. Si se omite, hereda la del producto principal.")
            UUID categoryId,

            @Schema(description = "Unidad en la que se cuenta esta variante. "
                    + "Si se omite, hereda la del producto principal.")
            UUID unitOfMeasureId
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

    @Schema(name = "UnitOfMeasureResponse", description = "Unidad de medida del catálogo global.")
    public record UnitOfMeasureResponse(
            @Schema(description = "Identificador único.") UUID id,
            @Schema(description = "Código de negocio.", example = "BOT") String code,
            @Schema(description = "Nombre.", example = "Botella") String name,
            @Schema(description = "Símbolo para mostrar.", example = "bot") String symbol
    ) {
    }

    @Schema(name = "ProductResponse", description = "Producto del catálogo.")
    public record ProductResponse(
            @Schema(description = "Identificador único.") UUID id,
            @Schema(description = "Organización a la que pertenece.") UUID organizationId,
            @Schema(description = "Producto principal del que es variante. Nulo si es principal.")
            UUID parentProductId,
            @Schema(description = "Categoría en la que se clasifica.") UUID categoryId,
            @Schema(description = "SKU.", example = "BEB-BRISA-BOT-1L") String sku,
            @Schema(description = "Código de barras.") String barcode,
            @Schema(description = "Nombre comercial.") String name,
            @Schema(description = "Descripción larga.") String description,
            @Schema(description = "Unidad en la que se cuenta su stock.") UnitOfMeasureResponse unit,
            @Schema(description = "Indica si el producto admite operaciones nuevas.") boolean active,
            @Schema(description = "Fecha de creación, en UTC.") Instant createdAt,
            @Schema(description = "Fecha de la última modificación, en UTC.") Instant updatedAt
    ) {
    }

    @Schema(name = "ProductFamilyResponse",
            description = "Un producto principal junto a sus variantes. Cada miembro se "
                    + "inventaría y se vende por separado; la familia solo los presenta juntos.")
    public record ProductFamilyResponse(
            @Schema(description = "Producto principal.") ProductResponse principal,
            @Schema(description = "Variantes. Vacío si el producto no tiene ninguna.")
            List<ProductResponse> variants
    ) {
    }
}
