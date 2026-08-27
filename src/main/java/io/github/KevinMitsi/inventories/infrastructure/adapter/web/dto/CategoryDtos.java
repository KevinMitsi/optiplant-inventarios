package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Contratos HTTP de categorías, agrupados por pertenecer al mismo recurso. */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    @Schema(name = "CreateCategoryRequest", description = "Datos para crear una categoría de productos.")
    public record CreateCategoryRequest(

            @Schema(description = "Código único dentro de la organización. Se normaliza a mayúsculas "
                    + "y es inmutable una vez creada.",
                    example = "BEB", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El código de la categoría es obligatorio.")
            @Size(max = 30, message = "El código no puede superar {max} caracteres.")
            @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                    message = "El código solo admite letras, números, punto, guion y guion bajo.")
            String code,

            @Schema(description = "Nombre de la categoría.", example = "Bebidas",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El nombre de la categoría es obligatorio.")
            @Size(max = 100, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Schema(description = "Descripción opcional.", example = "Bebidas frías y calientes")
            @Size(max = 250, message = "La descripción no puede superar {max} caracteres.")
            String description
    ) {
    }

    @Schema(name = "UpdateCategoryRequest", description = "Datos modificables de una categoría.")
    public record UpdateCategoryRequest(

            @Schema(description = "Nombre de la categoría.", example = "Bebidas y refrescos",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El nombre de la categoría es obligatorio.")
            @Size(max = 100, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Schema(description = "Descripción opcional.")
            @Size(max = 250, message = "La descripción no puede superar {max} caracteres.")
            String description
    ) {
    }

    @Schema(name = "CategoryResponse", description = "Categoría del catálogo.")
    public record CategoryResponse(
            @Schema(description = "Identificador único.") UUID id,
            @Schema(description = "Organización a la que pertenece.") UUID organizationId,
            @Schema(description = "Código de negocio.", example = "BEB") String code,
            @Schema(description = "Nombre.", example = "Bebidas") String name,
            @Schema(description = "Descripción.") String description,
            @Schema(description = "Indica si admite clasificar productos nuevos.") boolean active,
            @Schema(description = "Fecha de creación, en UTC.") Instant createdAt,
            @Schema(description = "Fecha de la última modificación, en UTC.") Instant updatedAt
    ) {
    }
}
