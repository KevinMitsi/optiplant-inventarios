package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.PurchaseOrderSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;

import java.util.UUID;

public interface QueryPurchaseOrderUseCase {

    PurchaseOrder getPurchaseOrderById(UUID purchaseOrderId);

    PageResult<PurchaseOrder> searchPurchaseOrders(PurchaseOrderSearchCriteria criteria, PageQuery pageQuery);
}
