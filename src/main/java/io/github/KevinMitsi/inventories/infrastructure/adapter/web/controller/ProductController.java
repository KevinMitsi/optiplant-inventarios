package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.result.ProductFamily;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ProductDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.CatalogWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo de productos y sus variantes (EP-03).
 *
 * <p>El producto es global a la organización y no almacena stock: las existencias pertenecen
 * a la pareja (sucursal, producto) y se gestionan en el módulo de inventario (RN-02).
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Productos",
     description = """
             Catálogo de productos, compartido por todas las sucursales. Un producto no \
             almacena existencias: el stock pertenece a la pareja (sucursal, producto) y vive \
             en el módulo de inventario (RN-02).

             Cada producto se cuenta en una sola unidad, la suya, sin factores de conversión. \
             Las presentaciones distintas del mismo artículo se modelan como variantes, que \
             son productos completos con SKU, stock y precio propios.""")
public class ProductController {

    private final ManageProductUseCase manageProductUseCase;
    private final QueryProductUseCase queryProductUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final CatalogWebMapper mapper;

    @PostMapping(value = "/organizations/{organizationId}/products",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "createProduct", summary = "Dar de alta un producto",
            description = """
                    Registra un producto en el catálogo (HU-07, RF-07).

                    El SKU es único dentro de la organización, se normaliza a mayúsculas y es \
                    inmutable una vez creado. El código de barras, si se informa, también debe \
                    ser único.

                    **La unidad de medida es obligatoria.** Es la unidad en la que se cuenta el \
                    stock del producto, sin factor de conversión de por medio.

                    **Las variantes son opcionales.** El producto que se crea es la variante \
                    principal; las que vengan en `variants` se crean junto a él, cada una como \
                    producto autónomo con su SKU, su stock y su precio.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Producto creado, con sus variantes si las trae.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductFamilyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza, o la organización no es la suya.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La organización, la categoría o la unidad no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "El SKU o el código de barras ya están en uso, aquí o en otra línea de la petición.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "La categoría es de otra organización o está dada de baja.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ProductDtos.ProductFamilyResponse> createProduct(
            @PathVariable UUID organizationId,
            @Valid @RequestBody ProductDtos.CreateProductRequest request) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "crear productos");

        ProductFamily family = manageProductUseCase.createProduct(mapper.toCommand(organizationId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/products/{id}")
                .buildAndExpand(family.principal().getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(family));
    }

    @GetMapping("/organizations/{organizationId}/products")
    @Operation(operationId = "searchProducts", summary = "Consultar el catálogo",
            description = """
                    Devuelve los productos de la organización, filtrados y paginados (HU-09).

                    Por defecto la página incluye principales y variantes, porque ambos se \
                    venden e inventarían igual. Para la vista agrupada del catálogo, filtre con \
                    `scope=PRINCIPALS_ONLY` y pida las variantes de cada uno por separado.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de productos.",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de paginación u ordenación inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "La organización no es la del usuario.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<ProductDtos.ProductResponse> searchProducts(
            @PathVariable UUID organizationId,

            @Parameter(description = "Filtra por categoría.")
            @RequestParam(required = false) UUID categoryId,

            @Parameter(description = "Búsqueda parcial sobre SKU, nombre y código de barras.",
                    example = "brisa")
            @RequestParam(required = false) String text,

            @Parameter(description = "Filtra por estado. Si se omite, devuelve activos e inactivos.")
            @RequestParam(required = false) Boolean active,

            @Parameter(description = "Qué parte de la familia devolver.",
                    schema = @Schema(allowableValues = {"ALL", "PRINCIPALS_ONLY", "VARIANTS_ONLY"},
                            defaultValue = "ALL"))
            @RequestParam(defaultValue = "ALL") String scope,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size,

            @Parameter(description = "Campo de ordenación.",
                    schema = @Schema(allowableValues = {"sku", "name", "barcode", "active",
                                                        "createdAt", "updatedAt"},
                            defaultValue = "name"))
            @RequestParam(required = false) String sortBy,

            @Parameter(schema = @Schema(allowableValues = {"ASC", "DESC"}, defaultValue = "ASC"))
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar el catálogo");

        PageResult<Product> result = queryProductUseCase.searchProducts(
                new ProductSearchCriteria(organizationId, categoryId, text, active,
                        ProductSearchCriteria.VariantScope.fromString(scope)),
                PageQuery.of(page, size, sortBy, SortDirection.fromString(sortDirection)));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/products/{productId}")
    @Operation(operationId = "getProductById", summary = "Consultar un producto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un producto con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductResponse getProductById(@PathVariable UUID productId) {
        return mapper.toResponse(queryProductUseCase.getProductById(productId));
    }

    @GetMapping("/products/{productId}/family")
    @Operation(operationId = "getProductFamily", summary = "Consultar un producto con sus variantes",
            description = "Si el producto consultado es a su vez una variante, la familia llega "
                    + "sin variantes: el catálogo es de un solo nivel.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto y variantes.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductFamilyResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un producto con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductFamilyResponse getProductFamily(@PathVariable UUID productId) {
        return mapper.toResponse(queryProductUseCase.getProductFamily(productId));
    }

    @GetMapping("/products/{productId}/variants")
    @Operation(operationId = "listProductVariants", summary = "Listar las variantes de un producto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Variantes del producto. Vacío si no tiene."),
            @ApiResponse(responseCode = "404", description = "No existe un producto con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<ProductDtos.ProductResponse> listVariants(@PathVariable UUID productId) {
        return mapper.toResponses(queryProductUseCase.listVariants(productId));
    }

    @PostMapping(value = "/products/{productId}/variants", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "addProductVariant", summary = "Añadir una variante",
            description = """
                    Cuelga una variante de un producto ya existente (HU-10, RF-09).

                    La variante es un producto completo: se inventaría, se compra y se vende \
                    por separado del principal. No comparte stock con él ni se convierte a su \
                    unidad.

                    Solo un producto principal admite variantes: el catálogo es de un solo \
                    nivel.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Variante creada.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "El producto, la categoría o la unidad no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El SKU o el código de barras ya están en uso.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "Se intentó colgar la variante de otra variante, o la categoría no es válida.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ProductDtos.ProductResponse> addVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductDtos.ProductVariantRequest request) {

        Product variant = manageProductUseCase.addVariant(mapper.toCommand(productId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/products/{id}")
                .buildAndExpand(variant.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(variant));
    }

    @PutMapping(value = "/products/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "updateProduct", summary = "Actualizar un producto",
            description = """
                    Modifica nombre, categoría, código de barras y descripción.

                    El SKU y la unidad de medida no son modificables: el stock y el histórico \
                    de movimientos están expresados en esa unidad, y cambiarla reinterpretaría \
                    cantidades ya registradas. Si la unidad cambia de verdad, lo correcto es \
                    dar de alta otro producto.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "El producto o la categoría no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El código de barras ya está en uso.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductResponse updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductDtos.UpdateProductRequest request) {

        return mapper.toResponse(manageProductUseCase.updateProduct(
                mapper.toCommand(productId, request)));
    }

    @PatchMapping("/products/{productId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "deactivateProduct", summary = "Dar de baja un producto",
            description = "Baja lógica. El producto aparece en ventas, compras y movimientos "
                    + "históricos, y eliminarlo dejaría ese histórico sin poder explicarse. "
                    + "Dar de baja un principal no da de baja sus variantes: son productos "
                    + "autónomos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto dado de baja.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un producto con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductResponse deactivateProduct(@PathVariable UUID productId) {
        return mapper.toResponse(manageProductUseCase.deactivateProduct(productId));
    }

    @PatchMapping("/products/{productId}/activation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "activateProduct", summary = "Reactivar un producto")
    @ApiResponse(responseCode = "200", description = "Producto reactivado.",
            content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class)))
    public ProductDtos.ProductResponse activateProduct(@PathVariable UUID productId) {
        return mapper.toResponse(manageProductUseCase.activateProduct(productId));
    }
}
