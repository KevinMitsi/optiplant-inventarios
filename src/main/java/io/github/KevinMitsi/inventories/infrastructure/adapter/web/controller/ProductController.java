package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Catálogo de productos y sus presentaciones (EP-03).
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
             en el módulo de inventario (RN-02).""")
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

                    **La unidad base es obligatoria.** Es la unidad en la que se contabiliza el \
                    stock, y sin ella el producto no podría recibir existencias. Su factor de \
                    conversión es 1 por definición. Las demás presentaciones se añaden después.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Producto creado con su unidad base.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza, o la organización no es la suya.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La organización, la categoría o la unidad no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El SKU o el código de barras ya están en uso.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "La categoría es de otra organización o está dada de baja.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ProductDtos.ProductResponse> createProduct(
            @PathVariable UUID organizationId,
            @Valid @RequestBody ProductDtos.CreateProductRequest request) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "crear productos");

        Product product = manageProductUseCase.createProduct(mapper.toCommand(organizationId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/products/{id}")
                .buildAndExpand(product.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(product));
    }

    @GetMapping("/organizations/{organizationId}/products")
    @Operation(operationId = "searchProducts", summary = "Consultar el catálogo",
            description = """
                    Devuelve los productos de la organización, filtrados y paginados (HU-09).

                    Cada producto llega con sus presentaciones. La página se resuelve en dos \
                    consultas fijas, no en una por producto.""")
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
                    example = "agua")
            @RequestParam(required = false) String text,

            @Parameter(description = "Filtra por estado. Si se omite, devuelve activos e inactivos.")
            @RequestParam(required = false) Boolean active,

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
                new ProductSearchCriteria(organizationId, categoryId, text, active),
                PageQuery.of(page, size, sortBy, SortDirection.fromString(sortDirection)));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/products/{productId}")
    @Operation(operationId = "getProductById", summary = "Consultar un producto",
            description = "Devuelve el producto con todas sus presentaciones.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un producto con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductResponse getProductById(@PathVariable UUID productId) {
        return mapper.toResponse(queryProductUseCase.getProductById(productId));
    }

    @PutMapping(value = "/products/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "updateProduct", summary = "Actualizar un producto",
            description = "Modifica nombre, categoría, código de barras y descripción. "
                    + "El SKU no es modificable.")
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

    @PostMapping(value = "/products/{productId}/units", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "addProductUnit", summary = "Añadir una presentación",
            description = """
                    Registra una presentación adicional del producto (HU-10, RF-09).

                    El factor indica cuántas unidades base equivale una de esta presentación: \
                    si la base es la botella, una caja de 24 tiene factor 24.

                    Un producto no puede tener dos presentaciones en la misma unidad de medida.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Presentación añadida.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "El producto o la unidad no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "El producto ya tiene una presentación en esa unidad, o el factor no es válido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductResponse addUnit(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductDtos.AddProductUnitRequest request) {

        return mapper.toResponse(manageProductUseCase.addUnit(mapper.toCommand(productId, request)));
    }

    @PutMapping(value = "/products/{productId}/units/{productUnitId}/factor",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "changeProductUnitFactor", summary = "Cambiar el factor de una presentación",
            description = "La unidad base no admite cambio de factor: siempre vale 1. "
                    + "Para alterarlo, designe otra presentación como base.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Factor actualizado.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "El producto no existe.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "Se intentó cambiar el factor de la unidad base, o la presentación no es del producto.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductResponse changeUnitFactor(
            @PathVariable UUID productId,
            @PathVariable UUID productUnitId,
            @Valid @RequestBody ProductDtos.ChangeUnitFactorRequest request) {

        return mapper.toResponse(manageProductUseCase.changeUnitFactor(
                mapper.toFactorCommand(productId, productUnitId, request.conversionFactor())));
    }

    @PostMapping(value = "/products/{productId}/base-unit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "changeBaseUnit", summary = "Cambiar la unidad base",
            description = """
                    Designa otra presentación como unidad base del producto.

                    Exige indicar el factor que pasa a tener la base anterior, porque su \
                    equivalencia con la nueva no es deducible: si la base pasa de botella a \
                    caja de 24, la botella pasa a valer 1/24, un dato que solo conoce el negocio.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unidad base cambiada.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "El producto no existe.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "La presentación indicada está dada de baja o no pertenece al producto.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductResponse changeBaseUnit(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductDtos.ChangeBaseUnitRequest request) {

        return mapper.toResponse(manageProductUseCase.changeBaseUnit(
                mapper.toCommand(productId, request)));
    }

    @PostMapping("/products/{productId}/units/{productUnitId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "deactivateProductUnit", summary = "Dar de baja una presentación",
            description = "La unidad base no se puede retirar: dejaría al producto sin "
                    + "referencia para medir su stock. Designe antes otra como base.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Presentación dada de baja.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "422", description = "Se intentó retirar la unidad base.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductResponse deactivateUnit(@PathVariable UUID productId,
                                                      @PathVariable UUID productUnitId) {
        return mapper.toResponse(manageProductUseCase.deactivateUnit(productId, productUnitId));
    }

    @PostMapping("/products/{productId}/units/{productUnitId}/activation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "activateProductUnit", summary = "Reactivar una presentación")
    @ApiResponse(responseCode = "200", description = "Presentación reactivada.",
            content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class)))
    public ProductDtos.ProductResponse activateUnit(@PathVariable UUID productId,
                                                    @PathVariable UUID productUnitId) {
        return mapper.toResponse(manageProductUseCase.activateUnit(productId, productUnitId));
    }

    @PostMapping("/products/{productId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "deactivateProduct", summary = "Dar de baja un producto",
            description = "Baja lógica. El producto aparece en ventas, compras y movimientos "
                    + "históricos, y eliminarlo dejaría ese histórico sin poder explicarse.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto dado de baja.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un producto con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.ProductResponse deactivateProduct(@PathVariable UUID productId) {
        return mapper.toResponse(manageProductUseCase.deactivateProduct(productId));
    }

    @PostMapping("/products/{productId}/activation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "activateProduct", summary = "Reactivar un producto")
    @ApiResponse(responseCode = "200", description = "Producto reactivado.",
            content = @Content(schema = @Schema(implementation = ProductDtos.ProductResponse.class)))
    public ProductDtos.ProductResponse activateProduct(@PathVariable UUID productId) {
        return mapper.toResponse(manageProductUseCase.activateProduct(productId));
    }
}
