package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.application.port.in.query.BranchSearchCriteria;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.BranchJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Traduce {@link BranchSearchCriteria} a una consulta con criterios de JPA.
 *
 * <p>Vive en infraestructura porque conoce la API de criterios y los nombres de los campos
 * persistentes. La capa de aplicación solo produce el objeto de criterios; cómo se convierta
 * en SQL no es asunto suyo.
 *
 * <p>Solo se añade una condición cuando el filtro correspondiente viene informado. La
 * consulta generada contiene únicamente lo que realmente se pidió, de modo que PostgreSQL
 * puede aprovechar los índices existentes en lugar de enfrentarse a comparaciones
 * neutralizadas por comprobaciones de nulidad.
 */
public final class BranchSpecifications {

    private BranchSpecifications() {
    }

    public static Specification<BranchJpaEntity> from(BranchSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Siempre presente: ninguna búsqueda puede cruzar la frontera de la organización.
            predicates.add(builder.equal(root.get("organizationId"), criteria.organizationId()));

            if (criteria.active() != null) {
                predicates.add(builder.equal(root.get("active"), criteria.active()));
            }

            if (criteria.city() != null) {
                predicates.add(builder.equal(
                        builder.lower(root.get("city")),
                        criteria.city().toLowerCase(Locale.ROOT)));
            }

            if (criteria.text() != null) {
                // Búsqueda parcial e insensible a mayúsculas sobre código o nombre.
                String pattern = "%" + criteria.text().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("code")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern)));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
