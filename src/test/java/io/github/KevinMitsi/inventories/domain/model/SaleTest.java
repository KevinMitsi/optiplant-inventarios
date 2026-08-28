package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Sale (venta, EP-06)")
class SaleTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();

    private SaleItem item;

    @BeforeEach
    void setUp() {
        item = SaleItem.create(UUID.randomUUID(), UUID.randomUUID(), Quantity.of("10"), Money.of("50.00"),
                Percentage.ZERO);
    }

    private Sale newSale() {
        return Sale.create(BRANCH_ID, CREATED_BY, null, "V-0001", Instant.now(), null, List.of(item));
    }

    @Nested
    @DisplayName("Ciclo de vida")
    class Lifecycle {

        @Test
        @DisplayName("nace en borrador")
        void createsAsDraft() {
            assertThat(newSale().getStatus()).isEqualTo(SaleStatus.DRAFT);
        }

        @Test
        @DisplayName("confirmar dos veces falla")
        void cannotConfirmTwice() {
            Sale sale = newSale();
            sale.confirm();

            assertThatThrownBy(sale::confirm).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("cancelar dos veces falla")
        void cannotCancelTwice() {
            Sale sale = newSale();
            sale.cancel();

            assertThatThrownBy(sale::cancel).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("una venta sin líneas es inválida")
        void rejectsEmptyItems() {
            assertThatThrownBy(() -> Sale.create(BRANCH_ID, CREATED_BY, null, "V-0002", Instant.now(), null,
                    List.of()))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    @DisplayName("wasConfirmed")
    class WasConfirmed {

        @Test
        @DisplayName("es falso en borrador")
        void falseWhileDraft() {
            assertThat(newSale().wasConfirmed()).isFalse();
        }

        @Test
        @DisplayName("es verdadero tras confirmar, y sigue siéndolo tras cancelar")
        void trueOnceConfirmedEvenAfterCancelling() {
            Sale sale = newSale();
            sale.confirm();
            assertThat(sale.wasConfirmed()).isTrue();

            sale.cancel();
            assertThat(sale.wasConfirmed()).isTrue();
        }
    }

    @Nested
    @DisplayName("total")
    class Total {

        @Test
        @DisplayName("suma los subtotales netos de cada línea")
        void sumsNetSubtotals() {
            SaleItem discounted = SaleItem.create(UUID.randomUUID(), UUID.randomUUID(), Quantity.of("2"),
                    Money.of("100.00"), Percentage.of("10"));
            Sale sale = Sale.create(BRANCH_ID, CREATED_BY, null, "V-0003", Instant.now(), null,
                    List.of(item, discounted));

            // item: 10 * 50.00 = 500.00 ; discounted: 2 * (100.00 - 10%) = 2 * 90.00 = 180.00
            assertThat(sale.total()).isEqualTo(Money.of("680.00"));
        }
    }
}
