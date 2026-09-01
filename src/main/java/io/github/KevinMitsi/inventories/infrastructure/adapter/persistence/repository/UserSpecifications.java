package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.application.port.in.query.UserSearchCriteria;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UserJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Traduce {@link UserSearchCriteria} a una consulta con criterios de JPA. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<UserJpaEntity> from(UserSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Siempre presente: ninguna búsqueda cruza la frontera de la organización.
            predicates.add(builder.equal(root.get("organizationId"), criteria.organizationId()));

            if (criteria.branchId() != null) {
                predicates.add(builder.equal(root.get("branchId"), criteria.branchId()));
            }

            if (criteria.active() != null) {
                predicates.add(builder.equal(root.get("active"), criteria.active()));
            }

            if (criteria.role() != null) {
                // Navega hasta el código del rol. La unión la resuelve el @EntityGraph
                // declarado en el repositorio, así que no añade una consulta extra.
                predicates.add(builder.equal(root.get("role").get("code"), criteria.role().name()));
            }

            if (criteria.text() != null) {
                String pattern = "%" + criteria.text().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("firstName")), pattern),
                        builder.like(builder.lower(root.get("lastName")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern)));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
