package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManagePurchaseOrderUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryPurchaseOrderUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreatePurchaseOrderCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceivePurchaseOrderItemCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.PurchaseOrderSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PurchaseOrderRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.SupplierRepositoryPort;
import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Percentage;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrderItem;
import io.github.KevinMitsi.inventories.domain.model.Quantity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@AuditedUseCase
public class PurchaseOrderUseCase implements ManagePurchaseOrderUseCase, QueryPurchaseOrderUseCase {

    private static final Logger log = Logger.getLogger(PurchaseOrderUseCase.class.getName());

    private static final String BRANCH = "la sucursal";
    private static final String SUPPLIER = "el proveedor";
    private static final String PRODUCT = "el producto";
    private static final String ORDER = "la orden de compra";

    private final PurchaseOrderRepositoryPort purchaseOrderRepository;
    private final BranchRepositoryPort branchRepository;
    private final SupplierRepositoryPort supplierRepository;
    private final ProductRepositoryPort productRepository;
    private final InventoryMovementPoster poster;

    public PurchaseOrderUseCase(PurchaseOrderRepositoryPort purchaseOrderRepository,
                        BranchRepositoryPort branchRepository,
                        SupplierRepositoryPort supplierRepository,
                        ProductRepositoryPort productRepository,
                        InventoryMovementPoster poster) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.branchRepository = branchRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.poster = poster;
    }

    @Override
    public PurchaseOrder createPurchaseOrder(CreatePurchaseOrderCommand command) {
        if (!branchRepository.existsById(command.branchId())) {
            throw new ResourceNotFoundException(BRANCH, command.branchId());
        }
        if (!supplierRepository.existsById(command.supplierId())) {
            throw new ResourceNotFoundException(SUPPLIER, command.supplierId());
        }
        if (purchaseOrderRepository.existsByBranchIdAndOrderNumber(command.branchId(), command.orderNumber())) {
            throw new DuplicateResourceException(ORDER, "número", command.orderNumber());
        }

        List<PurchaseOrderItem> items = command.items().stream()
                .map(this::toItem)
                .toList();

        PurchaseOrder order = PurchaseOrder.create(command.branchId(), command.supplierId(), command.createdBy(),
                command.orderNumber(), command.orderDate(), command.paymentTermDays(), command.notes(), items);

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        log.info(() -> "Orden de compra creada: id=%s, número=%s, líneas=%d"
                .formatted(saved.getId(), saved.getOrderNumber(), saved.getItems().size()));
        return saved;
    }

    @Override
    public PurchaseOrder confirmPurchaseOrder(UUID purchaseOrderId) {
        PurchaseOrder order = loadOrder(purchaseOrderId);
        order.confirm();
        return purchaseOrderRepository.save(order);
    }

    @Override
    public PurchaseOrder cancelPurchaseOrder(UUID purchaseOrderId) {
        PurchaseOrder order = loadOrder(purchaseOrderId);
        order.cancel();
        return purchaseOrderRepository.save(order);
    }

    @Override
    public PurchaseOrder receiveItem(ReceivePurchaseOrderItemCommand command) {
        PurchaseOrder order = loadOrder(command.purchaseOrderId());
        PurchaseOrderItem item = order.findItemById(command.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("la línea de la orden de compra", command.itemId()));

        Product product = requireProduct(item.getProductId());

        Quantity received = Quantity.of(command.quantityReceived());
        order.receiveItem(item.getId(), received);
        PurchaseOrder saved = purchaseOrderRepository.save(order);

        poster.post(new PostInventoryMovementCommand(saved.getBranchId(), item.getProductId(), product.getSku(),
                InventoryMovementType.PURCHASE_IN, received.value(), item.netUnitPrice().amount(),
                "Recepción de compra %s".formatted(saved.getOrderNumber()), command.userId(),
                Instant.now(), saved.getId(), null, null, null));

        log.info(() -> "Recepción registrada: orden=%s, línea=%s, cantidad=%s"
                .formatted(saved.getOrderNumber(), item.getId(), received));
        return saved;
    }

    @Override
    public PurchaseOrder getPurchaseOrderById(UUID purchaseOrderId) {
        return loadOrder(purchaseOrderId);
    }

    @Override
    public PageResult<PurchaseOrder> searchPurchaseOrders(PurchaseOrderSearchCriteria criteria, PageQuery pageQuery) {
        return purchaseOrderRepository.search(criteria, pageQuery);
    }

    private PurchaseOrderItem toItem(CreatePurchaseOrderCommand.Item item) {
        requireProduct(item.productId());

        return PurchaseOrderItem.create(item.productId(), Quantity.of(item.quantity()),
                Money.of(item.unitPrice()), Percentage.ofNullable(item.discountPercentage()));
    }

    private PurchaseOrder loadOrder(UUID purchaseOrderId) {
        return purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(ORDER, purchaseOrderId));
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, productId));
    }
}
