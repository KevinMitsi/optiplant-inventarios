package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PurchaseOrder (orden de compra, EP-05)")
class PurchaseOrderTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();

    private PurchaseOrderItem item;

    @BeforeEach
    void setUp() {
        item = PurchaseOrderItem.create(UUID.randomUUID(), Quantity.of("10"),
                Money.of("50.00"), Percentage.ZERO);
    }

    private PurchaseOrder newOrder() {
        return PurchaseOrder.create(BRANCH_ID, SUPPLIER_ID, CREATED_BY, "OC-0001", LocalDate.now(), 30, null,
                List.of(item));
    }

    @Nested
    @DisplayName("Ciclo de vida")
    class Lifecycle {

        @Test
        @DisplayName("nace en borrador")
        void createsAsDraft() {
            assertThat(newOrder().getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        }

        @Test
        @DisplayName("no se puede recibir mercancía sin confirmar antes")
        void cannotReceiveBeforeConfirmed() {
            PurchaseOrder order = newOrder();

            assertThatThrownBy(() -> order.receiveItem(item.getId(), Quantity.of("1")))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("confirmar dos veces falla")
        void cannotConfirmTwice() {
            PurchaseOrder order = newOrder();
            order.confirm();

            assertThatThrownBy(order::confirm).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("no se puede cancelar una orden ya recibida")
        void cannotCancelAfterReceipt() {
            PurchaseOrder order = newOrder();
            order.confirm();
            order.receiveItem(item.getId(), Quantity.of("10"));

            assertThatThrownBy(order::cancel).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("una orden sin líneas es inválida")
        void rejectsEmptyItems() {
            assertThatThrownBy(() -> PurchaseOrder.create(BRANCH_ID, SUPPLIER_ID, CREATED_BY, "OC-0002",
                    LocalDate.now(), 0, null, List.of()))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    @DisplayName("Recepción de mercancía (HU-19, RF-21)")
    class Receiving {

        @Test
        @DisplayName("recibir toda la cantidad de la única línea deja la orden RECEIVED")
        void fullReceiptClosesOrder() {
            PurchaseOrder order = newOrder();
            order.confirm();

            order.receiveItem(item.getId(), Quantity.of("10"));

            assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        }

        @Test
        @DisplayName("recibir menos de lo pedido deja la orden PARTIALLY_RECEIVED")
        void partialReceiptLeavesOrderPartiallyReceived() {
            PurchaseOrder order = newOrder();
            order.confirm();

            order.receiveItem(item.getId(), Quantity.of("4"));

            assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
            assertThat(order.findItemById(item.getId()).orElseThrow().getReceivedQuantity())
                    .isEqualTo(Quantity.of("4"));
        }

        @Test
        @DisplayName("dos recepciones parciales que completan la línea cierran la orden")
        void twoPartialReceiptsCanCompleteTheOrder() {
            PurchaseOrder order = newOrder();
            order.confirm();

            order.receiveItem(item.getId(), Quantity.of("6"));
            order.receiveItem(item.getId(), Quantity.of("4"));

            assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        }

        @Test
        @DisplayName("recibir por encima de lo pedido falla")
        void rejectsOverReceipt() {
            PurchaseOrder order = newOrder();
            order.confirm();

            assertThatThrownBy(() -> order.receiveItem(item.getId(), Quantity.of("11")))
                    .isInstanceOf(io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("una orden con dos líneas solo cierra cuando ambas están completas")
        void orderWithTwoItemsClosesOnlyWhenBothComplete() {
            PurchaseOrderItem second = PurchaseOrderItem.create(UUID.randomUUID(), Quantity.of("5"), Money.of("20.00"), Percentage.ZERO);
            PurchaseOrder order = PurchaseOrder.create(BRANCH_ID, SUPPLIER_ID, CREATED_BY, "OC-0003",
                    LocalDate.now(), 0, null, List.of(item, second));
            order.confirm();

            order.receiveItem(item.getId(), Quantity.of("10"));
            assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);

            order.receiveItem(second.getId(), Quantity.of("5"));
            assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        }
    }
}
