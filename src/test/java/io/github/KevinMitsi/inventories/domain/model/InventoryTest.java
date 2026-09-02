package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Inventory (saldo de un producto por sucursal)")
class InventoryTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private Inventory newInventory() {
        return Inventory.open(BRANCH_ID, PRODUCT_ID);
    }

    @Nested
    @DisplayName("Apertura")
    class Opening {

        @Test
        @DisplayName("un saldo nuevo nace en cero, sin mínimo y sin costo")
        void opensAtZero() {
            // Act
            Inventory inventory = newInventory();

            // Assert
            assertThat(inventory.getQuantity()).isEqualTo(Quantity.ZERO);
            assertThat(inventory.getMinimumStock()).isEqualTo(Quantity.ZERO);
            assertThat(inventory.getAverageCost()).isEqualTo(Money.ZERO);
            assertThat(inventory.isOutOfStock()).isTrue();
        }
    }

    @Nested
    @DisplayName("Costo promedio ponderado (RF-23, HU-21)")
    class WeightedAverageCost {

        @Test
        @DisplayName("la primera compra fija el costo promedio directamente")
        void firstPurchaseSetsCostDirectly() {
            // Arrange
            Inventory inventory = newInventory();

            // Act
            inventory.receiveWithCost(Quantity.of("10"), Money.of("100.00"));

            // Assert
            assertThat(inventory.getQuantity()).isEqualTo(Quantity.of("10"));
            assertThat(inventory.getAverageCost()).isEqualTo(Money.of("100.00"));
        }

        @Test
        @DisplayName("una segunda compra a otro precio pondera por cantidad")
        void secondPurchasePondersByQuantity() {
            // Arrange: 10 unidades a 100 -> saldo 1000
            Inventory inventory = newInventory();
            inventory.receiveWithCost(Quantity.of("10"), Money.of("100.00"));

            // Act: 10 unidades más a 200 -> saldo 1000 + 2000 = 3000 / 20 = 150
            inventory.receiveWithCost(Quantity.of("10"), Money.of("200.00"));

            // Assert
            assertThat(inventory.getQuantity()).isEqualTo(Quantity.of("20"));
            assertThat(inventory.getAverageCost().amount()).isEqualByComparingTo(new BigDecimal("150.0000"));
        }

        @Test
        @DisplayName("una salida no altera el costo promedio")
        void exitDoesNotChangeAverageCost() {
            // Arrange
            Inventory inventory = newInventory();
            inventory.receiveWithCost(Quantity.of("10"), Money.of("100.00"));

            // Act
            inventory.decrease(Quantity.of("4"));

            // Assert
            assertThat(inventory.getQuantity()).isEqualTo(Quantity.of("6"));
            assertThat(inventory.getAverageCost().amount()).isEqualByComparingTo(new BigDecimal("100.0000"));
        }
    }

    @Nested
    @DisplayName("Movimientos de saldo")
    class Movements {

        @Test
        @DisplayName("increase no requiere costo y suma directamente")
        void increaseAddsQuantity() {
            // Arrange
            Inventory inventory = newInventory();

            // Act
            inventory.increase(Quantity.of("5"));

            // Assert
            assertThat(inventory.getQuantity()).isEqualTo(Quantity.of("5"));
        }

        @Test
        @DisplayName("decrease por encima del saldo disponible falla")
        void decreaseBeyondAvailableFails() {
            // Arrange
            Inventory inventory = newInventory();
            inventory.increase(Quantity.of("3"));

            // Act & Assert
            assertThatThrownBy(() -> inventory.decrease(Quantity.of("4")))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    @DisplayName("Umbrales de reabastecimiento (RF-16, §34)")
    class Thresholds {

        @Test
        @DisplayName("sin mínimo configurado, nunca está en stock bajo")
        void neverLowStockWithoutMinimum() {
            // Arrange
            Inventory inventory = newInventory();
            inventory.increase(Quantity.of("1"));

            // Assert
            assertThat(inventory.isLowStock()).isFalse();
        }

        @Test
        @DisplayName("igual al mínimo cuenta como stock bajo")
        void equalToMinimumIsLowStock() {
            // Arrange
            Inventory inventory = newInventory();
            inventory.setMinimumStock(Quantity.of("10"));
            inventory.increase(Quantity.of("10"));

            // Assert
            assertThat(inventory.isLowStock()).isTrue();
            assertThat(inventory.isOutOfStock()).isFalse();
        }

        @Test
        @DisplayName("cantidad cero siempre está agotada, tenga o no mínimo")
        void zeroIsAlwaysOutOfStock() {
            // Arrange
            Inventory inventory = newInventory();

            // Assert
            assertThat(inventory.isOutOfStock()).isTrue();
        }
    }
}
