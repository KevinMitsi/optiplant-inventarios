package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.CreatePurchaseOrderCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceivePurchaseOrderItemCommand;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;

import java.util.UUID;

public interface ManagePurchaseOrderUseCase {

    PurchaseOrder createPurchaseOrder(CreatePurchaseOrderCommand command);

    PurchaseOrder confirmPurchaseOrder(UUID purchaseOrderId);

    PurchaseOrder cancelPurchaseOrder(UUID purchaseOrderId);

    /** Postea el {@code PURCHASE_IN} correspondiente e incrementa el inventario (RF-21). */
    PurchaseOrder receiveItem(ReceivePurchaseOrderItemCommand command);
}
