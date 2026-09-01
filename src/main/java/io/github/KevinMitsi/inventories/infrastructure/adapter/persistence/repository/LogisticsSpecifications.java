package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.application.port.in.query.CarrierSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.query.LogisticsRouteSearchCriteria;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.CarrierJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.LogisticsRouteJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Traduce los criterios de búsqueda de transportistas y rutas logísticas a consultas con criterios de JPA. */
public final class LogisticsSpecifications {

    private LogisticsSpecifications() {
    }

    public static Specification<CarrierJpaEntity> forCarriers(CarrierSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), criteria.organizationId()));

            if (criteria.active() != null) {
                predicates.add(builder.equal(root.get("active"), criteria.active()));
            }

            if (criteria.text() != null) {
                String pattern = "%" + criteria.text().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("code")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern)));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<LogisticsRouteJpaEntity> forLogisticsRoutes(LogisticsRouteSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), criteria.organizationId()));

            if (criteria.originBranchId() != null) {
                predicates.add(builder.equal(root.get("originBranchId"), criteria.originBranchId()));
            }
            if (criteria.destinationBranchId() != null) {
                predicates.add(builder.equal(root.get("destinationBranchId"), criteria.destinationBranchId()));
            }
            if (criteria.active() != null) {
                predicates.add(builder.equal(root.get("active"), criteria.active()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
