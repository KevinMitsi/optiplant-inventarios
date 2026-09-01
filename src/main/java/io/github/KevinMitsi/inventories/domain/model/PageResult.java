package io.github.KevinMitsi.inventories.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Página de resultados devuelta por un puerto de salida.
 *
 * <p>Contrapartida de {@link PageQuery}: mantiene el dominio libre de
 * {@code org.springframework.data.domain.Page}. El adaptador de persistencia construye
 * esto a partir del resultado de Spring Data, y el adaptador REST lo convierte en el DTO
 * de respuesta.
 *
 * @param content       elementos de esta página
 * @param page          índice de la página devuelta, empezando en 0
 * @param size          tamaño de página solicitado
 * @param totalElements total de elementos que cumplen el filtro, en todas las páginas
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <T> PageResult<T> of(List<T> content, PageQuery query, long totalElements) {
        return new PageResult<>(content, query.page(), query.size(), totalElements);
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), query.page(), query.size(), 0L);
    }

    /**
     * Transforma el contenido conservando los metadatos de paginación.
     *
     * <p>Es lo que usan los servicios para pasar de modelo de dominio a DTO sin recalcular
     * ni volver a consultar el total.
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "El mapeador no puede ser nulo.");
        List<R> mapped = content.stream().<R>map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public boolean isFirst() {
        return page == 0;
    }

    public boolean isLast() {
        return page >= totalPages() - 1;
    }

    public boolean hasNext() {
        return !isLast();
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public int numberOfElements() {
        return content.size();
    }
}
