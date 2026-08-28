package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.PurchaseOrderSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.PurchaseOrderRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PurchaseOrderJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.PurchasingPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.PurchaseOrderJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.PurchasingSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PurchaseOrderPersistenceAdapter implements PurchaseOrderRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of("orderNumber", "orderDate", "status", "createdAt");
    private static final String DEFAULT_SORT_FIELD = "orderDate";

    private final PurchaseOrderJpaRepository repository;
    private final PurchasingPersistenceMapper mapper;

    @Override
    public PurchaseOrder save(PurchaseOrder purchaseOrder) {
        return mapper.toDomain(repository.save(mapper.toEntity(purchaseOrder)));
    }

    @Override
    public Optional<PurchaseOrder> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByBranchIdAndOrderNumber(UUID branchId, String orderNumber) {
        return repository.existsByBranchIdAndOrderNumber(branchId, orderNumber);
    }

    @Override
    public PageResult<PurchaseOrder> search(PurchaseOrderSearchCriteria criteria, PageQuery pageQuery) {
        Page<PurchaseOrderJpaEntity> page = repository.findAll(
                PurchasingSpecifications.forPurchaseOrders(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
