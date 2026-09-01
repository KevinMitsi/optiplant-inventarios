package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.application.port.in.query.CategorySearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.CategoryJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Traduce los criterios de búsqueda del catálogo a consultas con criterios de JPA. */
public final class CatalogSpecifications {

    private CatalogSpecifications() {
    }

    public static Specification<CategoryJpaEntity> forCategories(CategorySearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), criteria.organizationId()));

            if (criteria.active() != null) {
                predicates.add(builder.equal(root.get("active"), criteria.active()));
            }

            if (criteria.text() != null) {
                String pattern = likePattern(criteria.text());
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("code")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern)));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<ProductJpaEntity> forProducts(ProductSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), criteria.organizationId()));

            if (criteria.categoryId() != null) {
                predicates.add(builder.equal(root.get("categoryId"), criteria.categoryId()));
            }

            if (criteria.active() != null) {
                predicates.add(builder.equal(root.get("active"), criteria.active()));
            }

            if (criteria.text() != null) {
                String pattern = likePattern(criteria.text());
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("sku")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("barcode")), pattern)));
            }

            switch (criteria.scope()) {
                case PRINCIPALS_ONLY -> predicates.add(root.get("parentProductId").isNull());
                case VARIANTS_ONLY -> predicates.add(root.get("parentProductId").isNotNull());
                case ALL -> {
                    // Sin filtro: principales y variantes son igual de vendibles e inventariables.
                }
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String likePattern(String text) {
        return "%" + text.toLowerCase(Locale.ROOT) + "%";
    }
}
