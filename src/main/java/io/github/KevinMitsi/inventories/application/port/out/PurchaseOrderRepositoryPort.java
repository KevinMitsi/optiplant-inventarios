package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.PurchaseOrderSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepositoryPort {

    PurchaseOrder save(PurchaseOrder purchaseOrder);

    /** Carga la orden con sus líneas: el agregado nunca se devuelve incompleto. */
    Optional<PurchaseOrder> findById(UUID id);

    boolean existsByBranchIdAndOrderNumber(UUID branchId, String orderNumber);

    PageResult<PurchaseOrder> search(PurchaseOrderSearchCriteria criteria, PageQuery pageQuery);
}
