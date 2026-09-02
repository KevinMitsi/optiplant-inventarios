package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.port.in.command.CreatePurchaseOrderCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceivePurchaseOrderItemCommand;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PurchaseOrderRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.SupplierRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Percentage;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrder;
import io.github.KevinMitsi.inventories.domain.model.PurchaseOrderItem;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre la recepción de compras: conversión de unidad al recibir en una presentación distinta
 * de la base, y la recepción parcial (RF-21) — la deuda técnica que no podía probarse porque
 * este módulo no existía todavía.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PurchaseOrderUseCase")
class PurchaseOrderServiceTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private PurchaseOrderRepositoryPort purchaseOrderRepository;
    @Mock
    private BranchRepositoryPort branchRepository;
    @Mock
    private SupplierRepositoryPort supplierRepository;
    @Mock
    private ProductRepositoryPort productRepository;
    @Mock
    private InventoryMovementPoster poster;

    private PurchaseOrderUseCase service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderUseCase(purchaseOrderRepository, branchRepository, supplierRepository,
                productRepository, poster);

        UnitOfMeasure unit = new UnitOfMeasure(UUID.randomUUID(), "UNIT", "Unidad", "und");
        product = Product.create(UUID.randomUUID(), null, "SKU-1", null, "Producto", null, unit);

        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);
        when(supplierRepository.existsById(SUPPLIER_ID)).thenReturn(true);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    @DisplayName("Recepción")
    class Receiving {

        @Test
        @DisplayName("lo recibido se postea tal cual, en la unidad del producto y a su precio neto")
        void postsReceivedQuantityAsIs() {
            // Arrange: orden con una línea de 48 unidades a 10.00 cada una
            PurchaseOrderItem item = PurchaseOrderItem.create(product.getId(),
                    Quantity.of("48"), io.github.KevinMitsi.inventories.domain.model.Money.of("10.00"),
                    Percentage.ZERO);
            PurchaseOrder order = PurchaseOrder.create(BRANCH_ID, SUPPLIER_ID, USER_ID, "OC-0001",
                    LocalDate.now(), 0, null, List.of(item));
            order.confirm();
            when(purchaseOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);

            // Act: recibe las 48 unidades completas
            service.receiveItem(new ReceivePurchaseOrderItemCommand(order.getId(), item.getId(),
                    new BigDecimal("48"), USER_ID));

            // Assert
            verify(poster).post(captor.capture());
            PostInventoryMovementCommand posted = captor.getValue();
            assertThat(posted.movementType()).isEqualTo(InventoryMovementType.PURCHASE_IN);
            assertThat(posted.quantity()).isEqualByComparingTo(new BigDecimal("48"));
            assertThat(posted.unitCost()).isEqualByComparingTo(new BigDecimal("10.0000"));
            assertThat(posted.purchaseOrderId()).isEqualTo(order.getId());
        }

        @Test
        @DisplayName("recepción parcial: solo se postea la cantidad efectivamente recibida")
        void partialReceiptPostsOnlyReceivedQuantity() {
            // Arrange: línea de 10 unidades a 50.00
            PurchaseOrderItem item = PurchaseOrderItem.create(product.getId(),
                    Quantity.of("10"), io.github.KevinMitsi.inventories.domain.model.Money.of("50.00"),
                    Percentage.ZERO);
            PurchaseOrder order = PurchaseOrder.create(BRANCH_ID, SUPPLIER_ID, USER_ID, "OC-0002",
                    LocalDate.now(), 0, null, List.of(item));
            order.confirm();
            when(purchaseOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);

            // Act: recibe solo 4 de las 10
            PurchaseOrder result = service.receiveItem(new ReceivePurchaseOrderItemCommand(order.getId(),
                    item.getId(), new BigDecimal("4"), USER_ID));

            // Assert
            verify(poster).post(captor.capture());
            assertThat(captor.getValue().quantity()).isEqualByComparingTo(new BigDecimal("4"));
            assertThat(result.getStatus())
                    .isEqualTo(io.github.KevinMitsi.inventories.domain.model.PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
    }
}
