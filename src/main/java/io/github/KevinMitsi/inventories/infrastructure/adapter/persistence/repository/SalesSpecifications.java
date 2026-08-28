package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.application.port.in.query.PriceListSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.query.SaleSearchCriteria;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PriceListJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SaleJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Traduce los criterios de búsqueda de ventas y listas de precios a consultas con criterios de JPA. */
public final class SalesSpecifications {

    private SalesSpecifications() {
    }

    public static Specification<PriceListJpaEntity> forPriceLists(PriceListSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), criteria.organizationId()));

            if (criteria.active() != null) {
                predicates.add(builder.equal(root.get("active"), criteria.active()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<SaleJpaEntity> forSales(SaleSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.branchId() != null) {
                predicates.add(builder.equal(root.get("branchId"), criteria.branchId()));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status().name()));
            }
            if (criteria.fromDate() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("saleDate"), criteria.fromDate()));
            }
            if (criteria.toDate() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("saleDate"), criteria.toDate()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
