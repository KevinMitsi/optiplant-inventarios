package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.application.port.in.query.ActivityLogSearchCriteria;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ActivityLogJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Traduce los criterios de búsqueda de la traza de auditoría a consultas con criterios de JPA. */
public final class ActivityLogSpecifications {

    private ActivityLogSpecifications() {
    }

    public static Specification<ActivityLogJpaEntity> forActivityLogs(ActivityLogSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Los registros del sistema no tienen organización. Se incluyen solo si se piden
            // de forma explícita: mezclarlos siempre con los de la organización llenaría el
            // panel de sucesos de arranque que nadie estaba buscando.
            Predicate ownOrganization = builder.equal(root.get("organizationId"), criteria.organizationId());
            predicates.add(criteria.includeSystem()
                    ? builder.or(ownOrganization, builder.isNull(root.get("organizationId")))
                    : ownOrganization);

            if (criteria.username() != null) {
                predicates.add(builder.equal(
                        builder.lower(root.get("username")), criteria.username().toLowerCase(Locale.ROOT)));
            }

            if (criteria.role() != null) {
                predicates.add(builder.equal(root.get("role"), criteria.role().toUpperCase(Locale.ROOT)));
            }

            if (criteria.level() != null) {
                predicates.add(builder.equal(root.get("level"), criteria.level().toUpperCase(Locale.ROOT)));
            }

            if (criteria.useCase() != null) {
                predicates.add(builder.like(
                        builder.lower(root.get("useCase")), likePattern(criteria.useCase())));
            }

            if (criteria.text() != null) {
                predicates.add(builder.like(
                        builder.lower(root.get("operation")), likePattern(criteria.text())));
            }

            if (criteria.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), criteria.from()));
            }

            if (criteria.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), criteria.to()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String likePattern(String text) {
        return "%" + text.toLowerCase(Locale.ROOT) + "%";
    }
}
