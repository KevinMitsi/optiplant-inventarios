package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageCategoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryCategoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.CategorySearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CategoryDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
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

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Categorías",
     description = "Clasificación del catálogo de productos. Es global a la organización, "
             + "igual que el propio catálogo.")
public class CategoryController {

    private final ManageCategoryUseCase manageCategoryUseCase;
    private final QueryCategoryUseCase queryCategoryUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final CatalogWebMapper mapper;

    @PostMapping(value = "/organizations/{organizationId}/categories",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "createCategory", summary = "Crear una categoría",
            description = "Registra una categoría. El código es único dentro de la organización, "
                    + "se normaliza a mayúsculas y es inmutable una vez creada.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoría creada.",
                    content = @Content(schema = @Schema(implementation = CategoryDtos.CategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza, o la organización no es la suya.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La organización no existe.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe una categoría con ese código.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CategoryDtos.CategoryResponse> createCategory(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CategoryDtos.CreateCategoryRequest request) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "crear categorías");

        Category category = manageCategoryUseCase.createCategory(
                mapper.toCommand(organizationId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/categories/{id}")
                .buildAndExpand(category.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(category));
    }

    @GetMapping("/organizations/{organizationId}/categories")
    @Operation(operationId = "searchCategories", summary = "Listar categorías",
            description = "Devuelve las categorías de la organización, filtradas y paginadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de categorías.",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de paginación u ordenación inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "La organización no es la del usuario.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<CategoryDtos.CategoryResponse> searchCategories(
            @PathVariable UUID organizationId,

            @Parameter(description = "Búsqueda parcial sobre código y nombre.")
            @RequestParam(required = false) String text,

            @Parameter(description = "Filtra por estado. Si se omite, devuelve activas e inactivas.")
            @RequestParam(required = false) Boolean active,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size,

            @Parameter(description = "Campo de ordenación.",
                    schema = @Schema(allowableValues = {"code", "name", "active", "createdAt", "updatedAt"},
                            defaultValue = "name"))
            @RequestParam(required = false) String sortBy,

            @Parameter(schema = @Schema(allowableValues = {"ASC", "DESC"}, defaultValue = "ASC"))
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar categorías");

        PageResult<Category> result = queryCategoryUseCase.searchCategories(
                new CategorySearchCriteria(organizationId, text, active),
                PageQuery.of(page, size, sortBy, SortDirection.fromString(sortDirection)));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/categories/{categoryId}")
    @Operation(operationId = "getCategoryById", summary = "Consultar una categoría")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría encontrada.",
                    content = @Content(schema = @Schema(implementation = CategoryDtos.CategoryResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una categoría con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CategoryDtos.CategoryResponse getCategoryById(@PathVariable UUID categoryId) {
        return mapper.toResponse(queryCategoryUseCase.getCategoryById(categoryId));
    }

    @PutMapping(value = "/categories/{categoryId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "updateCategory", summary = "Actualizar una categoría",
            description = "Modifica nombre y descripción. El código no es modificable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría actualizada.",
                    content = @Content(schema = @Schema(implementation = CategoryDtos.CategoryResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una categoría con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CategoryDtos.CategoryResponse updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryDtos.UpdateCategoryRequest request) {

        return mapper.toResponse(manageCategoryUseCase.updateCategory(
                mapper.toCommand(categoryId, request)));
    }

    @PostMapping("/categories/{categoryId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "deactivateCategory", summary = "Dar de baja una categoría",
            description = """
                    Retira la categoría del catálogo. Es baja lógica: los productos históricos \
                    siguen apuntando a ella.

                    **No se puede dar de baja si aún tiene productos activos**: quedarían \
                    clasificados en una categoría retirada, y el catálogo perdería coherencia \
                    sin que nada lo advirtiera. Reclasifique o dé de baja esos productos primero.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría dada de baja.",
                    content = @Content(schema = @Schema(implementation = CategoryDtos.CategoryResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una categoría con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "La categoría aún tiene productos activos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CategoryDtos.CategoryResponse deactivateCategory(@PathVariable UUID categoryId) {
        return mapper.toResponse(manageCategoryUseCase.deactivateCategory(categoryId));
    }

    @PostMapping("/categories/{categoryId}/activation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "activateCategory", summary = "Reactivar una categoría")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría reactivada.",
                    content = @Content(schema = @Schema(implementation = CategoryDtos.CategoryResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una categoría con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CategoryDtos.CategoryResponse activateCategory(@PathVariable UUID categoryId) {
        return mapper.toResponse(manageCategoryUseCase.activateCategory(categoryId));
    }
}
