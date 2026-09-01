package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio de paginación común a todos los listados de la API.
 *
 * <p>Se define aquí en lugar de serializar directamente el {@code Page} de Spring Data,
 * cuyo JSON incluye la estructura interna del {@code Pageable} y sus objetos de ordenación.
 * Ese formato es verboso, difícil de documentar y —lo importante— cambia entre versiones de
 * Spring Data, con lo que una actualización de la dependencia rompería a los clientes.
 * Este contrato es estable porque lo controlamos nosotros.
 *
 * @param <T> tipo de los elementos de la página
 */
@Schema(name = "PageResponse", description = "Página de resultados con sus metadatos de navegación.")
public record PageResponse<T>(

        @Schema(description = "Elementos de la página actual.")
        List<T> content,

        @Schema(description = "Índice de la página devuelta, empezando en 0.", example = "0")
        int page,

        @Schema(description = "Número de elementos solicitados por página.", example = "20")
        int size,

        @Schema(description = "Elementos de esta página concreta.", example = "20")
        int numberOfElements,

        @Schema(description = "Total de elementos que cumplen el filtro, en todas las páginas.",
                example = "137")
        long totalElements,

        @Schema(description = "Número total de páginas.", example = "7")
        int totalPages,

        @Schema(description = "Indica si es la primera página.", example = "true")
        boolean first,

        @Schema(description = "Indica si es la última página.", example = "false")
        boolean last,

        @Schema(description = "Indica si existe una página siguiente.", example = "true")
        boolean hasNext
) {

    /**
     * Construye la respuesta a partir de un resultado de dominio, transformando cada
     * elemento a su DTO.
     *
     * <p>Los metadatos se toman del resultado original: no se recalculan ni se vuelve a
     * consultar el total.
     */
    public static <D, R> PageResponse<R> from(PageResult<D> result, Function<D, R> toDto) {
        return new PageResponse<>(
                result.content().stream().map(toDto).toList(),
                result.page(),
                result.size(),
                result.numberOfElements(),
                result.totalElements(),
                result.totalPages(),
                result.isFirst(),
                result.isLast(),
                result.hasNext());
    }
}
