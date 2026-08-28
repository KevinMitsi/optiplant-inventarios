package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.query.InventorySearchCriteria;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryAlertJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.InventoryJpaEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Traduce los criterios de búsqueda de inventario y alertas a consultas con criterios de JPA. */
public final class InventorySpecifications {

    private InventorySpecifications() {
    }

    public static Specification<InventoryJpaEntity> forInventory(InventorySearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("branchId"), criteria.branchId()));

            if (Boolean.TRUE.equals(criteria.lowStockOnly())) {
                predicates.add(builder.lessThanOrEqualTo(root.get("quantity"), root.get("minimumStock")));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * {@code InventoryAlert} no referencia la sucursal directamente, solo el saldo al que
     * pertenece (mismo criterio de normalización que el resto del dominio: la sucursal se
     * obtiene por relación, no se duplica). Filtrar por sucursal exige, por tanto, una
     * subconsulta contra {@code inventory}.
     */
    public static Specification<InventoryAlertJpaEntity> forAlerts(InventoryAlertSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status().name()));
            }

            if (criteria.branchId() != null) {
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<InventoryJpaEntity> inventoryRoot = subquery.from(InventoryJpaEntity.class);
                subquery.select(inventoryRoot.get("id"))
                        .where(builder.equal(inventoryRoot.get("branchId"), criteria.branchId()));
                predicates.add(root.get("inventoryId").in(subquery));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
