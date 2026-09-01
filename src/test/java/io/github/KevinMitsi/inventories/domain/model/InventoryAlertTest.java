package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InventoryAlert (alerta de reabastecimiento, §34)")
class InventoryAlertTest {

    private static final UUID INVENTORY_ID = UUID.randomUUID();

    @Test
    @DisplayName("nace abierta")
    void opensAsOpen() {
        // Act
        InventoryAlert alert = InventoryAlert.open(INVENTORY_ID, InventoryAlertType.LOW_STOCK,
                Quantity.of("2"), Quantity.of("10"));

        // Assert
        assertThat(alert.getStatus()).isEqualTo(InventoryAlertStatus.OPEN);
        assertThat(alert.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("resolver marca fecha de cierre")
    void resolveSetsResolvedAt() {
        // Arrange
        InventoryAlert alert = InventoryAlert.open(INVENTORY_ID, InventoryAlertType.LOW_STOCK,
                Quantity.of("2"), Quantity.of("10"));

        // Act
        alert.resolve();

        // Assert
        assertThat(alert.getStatus()).isEqualTo(InventoryAlertStatus.RESOLVED);
        assertThat(alert.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("no se puede resolver dos veces")
    void cannotResolveTwice() {
        // Arrange
        InventoryAlert alert = InventoryAlert.open(INVENTORY_ID, InventoryAlertType.OUT_OF_STOCK,
                Quantity.ZERO, Quantity.of("10"));
        alert.resolve();

        // Act & Assert
        assertThatThrownBy(alert::resolve).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("descartar una alerta ya resuelta falla")
    void cannotDismissAfterResolved() {
        // Arrange
        InventoryAlert alert = InventoryAlert.open(INVENTORY_ID, InventoryAlertType.LOW_STOCK,
                Quantity.of("2"), Quantity.of("10"));
        alert.resolve();

        // Act & Assert
        assertThatThrownBy(alert::dismiss).isInstanceOf(InvalidStateTransitionException.class);
    }
}
