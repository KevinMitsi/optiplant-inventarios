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

/** Puente entre la paginación del dominio y la de Spring Data. */
public final class PageQueryTranslator {

    private PageQueryTranslator() {
    }

    /**
     * @param sortableFields lista blanca de campos ordenables. El nombre llega desde un
     *                       parámetro de la petición y acaba dentro de una consulta, así que
     *                       se contrasta en lugar de confiar en él.
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

    public static <E, D> PageResult<D> toPageResult(Page<E> page, Function<E, D> toDomain) {
        return new PageResult<>(
                page.getContent().stream().map(toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
