package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.application.port.in.query.PurchaseOrderSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.query.SupplierSearchCriteria;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PurchaseOrderItemJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PurchaseOrderJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SupplierJpaEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Traduce los criterios de búsqueda de proveedores y compras a consultas con criterios de JPA. */
public final class PurchasingSpecifications {

    private PurchasingSpecifications() {
    }

    public static Specification<SupplierJpaEntity> forSuppliers(SupplierSearchCriteria criteria) {
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

    /**
     * El filtro por producto exige una subconsulta contra {@code purchase_order_item}: sostiene
     * el histórico "por proveedor y producto" que pide RF-22/HU-20 sin desnormalizar el
     * producto en la cabecera de la orden.
     */
    public static Specification<PurchaseOrderJpaEntity> forPurchaseOrders(PurchaseOrderSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.branchId() != null) {
                predicates.add(builder.equal(root.get("branchId"), criteria.branchId()));
            }
            if (criteria.supplierId() != null) {
                predicates.add(builder.equal(root.get("supplierId"), criteria.supplierId()));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status().name()));
            }
            if (criteria.productId() != null) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<PurchaseOrderItemJpaEntity> itemRoot = subquery.from(PurchaseOrderItemJpaEntity.class);
                subquery.select(itemRoot.get("purchaseOrder").get("id"))
                        .where(builder.equal(itemRoot.get("productId"), criteria.productId()));
                predicates.add(root.get("id").in(subquery));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
