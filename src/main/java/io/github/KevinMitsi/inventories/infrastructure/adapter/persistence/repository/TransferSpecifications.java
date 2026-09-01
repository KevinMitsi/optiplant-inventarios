package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.application.port.in.query.TransferSearchCriteria;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.TransferJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Traduce los criterios de búsqueda de transferencias a consultas con criterios de JPA. */
public final class TransferSpecifications {

    private TransferSpecifications() {
    }

    /**
     * {@code branchId} se compara contra origen y destino a la vez (RF-46, HU-35, HU-41): una
     * sucursal necesita ver tanto lo que solicitó como lo que le están por enviar.
     */
    public static Specification<TransferJpaEntity> forTransfers(TransferSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.branchId() != null) {
                predicates.add(builder.or(
                        builder.equal(root.get("originBranchId"), criteria.branchId()),
                        builder.equal(root.get("destinationBranchId"), criteria.branchId())));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status().name()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
