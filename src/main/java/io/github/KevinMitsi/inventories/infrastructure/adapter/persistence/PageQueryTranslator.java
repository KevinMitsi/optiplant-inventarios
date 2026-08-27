package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.function.Function;

/**
 * Puente entre la paginación del dominio y la de Spring Data.
 *
 * <p>Existe para que {@code PageQuery} y {@code PageResult} puedan seguir siendo tipos
 * propios sin que cada adaptador repita la conversión. Al concentrarla aquí, cambiar de
 * tecnología de persistencia significa reescribir esta clase y nada más.
 */
public final class PageQueryTranslator {

    private PageQueryTranslator() {
    }

    /**
     * Convierte a {@link Pageable} validando el campo de ordenación contra una lista blanca.
     *
     * <p>La validación no es cosmética. El nombre del campo llega desde un parámetro de la
     * petición y acaba dentro de una consulta; aceptarlo sin filtrar permitiría ordenar por
     * columnas que no deberían ser visibles, o provocar un error en ejecución con un nombre
     * inexistente. La lista blanca la define cada adaptador, que es quien sabe qué campos
     * tienen índice y sentido de negocio.
     *
     * @param pageQuery      paginación pedida
     * @param sortableFields nombres de propiedad admitidos para ordenar
     * @param defaultSort    campo por el que ordenar si no se indicó ninguno
     * @throws DomainValidationException si se pide ordenar por un campo no admitido
     */
    public static Pageable toPageable(PageQuery pageQuery, Set<String> sortableFields, String defaultSort) {
        if (!pageQuery.isSorted()) {
            return PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by(defaultSort).ascending());
        }

        String field = pageQuery.sortBy();
        if (!sortableFields.contains(field)) {
            throw new DomainValidationException("sort",
                    "No se puede ordenar por '%s'. Campos admitidos: %s."
                            .formatted(field, String.join(", ", sortableFields.stream().sorted().toList())));
        }

        Sort sort = pageQuery.sortDirection().isDescending()
                ? Sort.by(field).descending()
                : Sort.by(field).ascending();

        return PageRequest.of(pageQuery.page(), pageQuery.size(), sort);
    }

    /**
     * Convierte una página de Spring Data en {@link PageResult}, aplicando el mapeo de cada
     * elemento a su tipo de dominio.
     */
    public static <E, D> PageResult<D> toPageResult(Page<E> page, Function<E, D> toDomain) {
        return new PageResult<>(
                page.getContent().stream().map(toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
