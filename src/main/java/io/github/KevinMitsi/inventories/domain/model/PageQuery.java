package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

/**
 * Petición de una página de resultados, expresada en términos del dominio.
 *
 * <p>Existe para que los puertos de salida no tengan que hablar de {@code Pageable}.
 * Si el dominio dependiera del tipo de Spring Data, cambiar de tecnología de persistencia
 * obligaría a tocar todas las interfaces de repositorio, que es justo lo que la
 * arquitectura hexagonal pretende evitar. El adaptador JPA traduce este objeto a
 * {@code Pageable} y el resultado de vuelta a {@link PageResult}.
 *
 * @param page          índice de página, empezando en 0
 * @param size          número de elementos por página
 * @param sortBy        propiedad del modelo de dominio por la que ordenar; nula si no aplica
 * @param sortDirection sentido de la ordenación
 */
public record PageQuery(int page, int size, String sortBy, SortDirection sortDirection) {

    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Techo de elementos por página.
     *
     * <p>Sin este límite, un cliente podría pedir {@code size=1000000} y forzar al servidor
     * a materializar la tabla entera en memoria. Es una defensa de disponibilidad, no una
     * comodidad: por eso se aplica en el dominio y no solo en el controlador (RNF-07).
     */
    public static final int MAX_PAGE_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new DomainValidationException("El número de página no puede ser negativo: %d.".formatted(page));
        }
        if (size <= 0) {
            throw new DomainValidationException("El tamaño de página debe ser mayor que cero: %d.".formatted(size));
        }
        if (size > MAX_PAGE_SIZE) {
            throw new DomainValidationException(
                    "El tamaño de página no puede superar %d: %d.".formatted(MAX_PAGE_SIZE, size));
        }
        if (sortDirection == null) {
            sortDirection = SortDirection.ASC;
        }
        if (sortBy != null && sortBy.isBlank()) {
            sortBy = null;
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size, null, SortDirection.ASC);
    }

    public static PageQuery of(int page, int size, String sortBy, SortDirection direction) {
        return new PageQuery(page, size, sortBy, direction);
    }

    public static PageQuery firstPage() {
        return of(0, DEFAULT_PAGE_SIZE);
    }

    public boolean isSorted() {
        return sortBy != null;
    }

    /** Número de elementos a saltar. Útil para adaptadores que trabajan con desplazamiento. */
    public long offset() {
        return (long) page * size;
    }
}
