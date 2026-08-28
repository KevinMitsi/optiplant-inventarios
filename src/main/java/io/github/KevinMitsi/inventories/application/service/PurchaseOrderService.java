package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManagePurchaseOrderUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryPurchaseOrderUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreatePurchaseOrderCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceivePurchaseOrderItemCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.PurchaseOrderSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;
import io.github.KevinMitsi.inventories.domain.usecase.PurchaseOrderUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Primary
@Service
@Transactional(rollbackFor = Exception.class)
public class PurchaseOrderService implements ManagePurchaseOrderUseCase, QueryPurchaseOrderUseCase {

    private final PurchaseOrderUseCase useCase;

    public PurchaseOrderService(PurchaseOrderUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public PurchaseOrder createPurchaseOrder(CreatePurchaseOrderCommand command) {
        return useCase.createPurchaseOrder(command);
    }

    @Override
    public PurchaseOrder confirmPurchaseOrder(UUID purchaseOrderId) {
        return useCase.confirmPurchaseOrder(purchaseOrderId);
    }

    @Override
    public PurchaseOrder cancelPurchaseOrder(UUID purchaseOrderId) {
        return useCase.cancelPurchaseOrder(purchaseOrderId);
    }

    @Override
    public PurchaseOrder receiveItem(ReceivePurchaseOrderItemCommand command) {
        return useCase.receiveItem(command);
    }

    @Override
    public PurchaseOrder getPurchaseOrderById(UUID purchaseOrderId) {
        return useCase.getPurchaseOrderById(purchaseOrderId);
    }

    @Override
    public PageResult<PurchaseOrder> searchPurchaseOrders(PurchaseOrderSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchPurchaseOrders(criteria, pageQuery);
    }
}
