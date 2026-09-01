package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InventoryMovement (histórico inmutable, RN-04/RN-11)")
class InventoryMovementTest {

    private static final UUID INVENTORY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("un movimiento válido conserva sus datos tal cual")
    void createsMovement() {
        // Act
        InventoryMovement movement = InventoryMovement.create(INVENTORY_ID, InventoryMovementType.PURCHASE_IN,
                USER_ID, Quantity.of("10"), Money.of("50.00"), "Compra a proveedor",
                UUID.randomUUID(), null, null, null, Instant.now());

        // Assert
        assertThat(movement.getMovementType()).isEqualTo(InventoryMovementType.PURCHASE_IN);
        assertThat(movement.getQuantity()).isEqualTo(Quantity.of("10"));
        assertThat(movement.getPurchaseOrderId()).isNotNull();
    }

    @Test
    @DisplayName("rechaza cantidad cero o negativa")
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> InventoryMovement.create(INVENTORY_ID, InventoryMovementType.LOSS_OUT,
                USER_ID, Quantity.ZERO, null, "Merma", null, null, null, null, Instant.now()))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("rechaza un motivo vacío (RN-11)")
    void rejectsBlankReason() {
        assertThatThrownBy(() -> InventoryMovement.create(INVENTORY_ID, InventoryMovementType.LOSS_OUT,
                USER_ID, Quantity.of("1"), null, "   ", null, null, null, null, Instant.now()))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("rechaza referenciar más de un documento de origen a la vez")
    void rejectsMoreThanOneReference() {
        assertThatThrownBy(() -> InventoryMovement.create(INVENTORY_ID, InventoryMovementType.SALE_OUT,
                USER_ID, Quantity.of("1"), null, "Venta", UUID.randomUUID(), UUID.randomUUID(), null, null,
                Instant.now()))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("sin documento de origen es válido: es el movimiento manual libre")
    void allowsNoReferenceAtAll() {
        // Act
        InventoryMovement movement = InventoryMovement.create(INVENTORY_ID, InventoryMovementType.RETURN_IN,
                USER_ID, Quantity.of("1"), null, "Devolución de cliente", null, null, null, null, Instant.now());

        // Assert
        assertThat(movement.getPurchaseOrderId()).isNull();
        assertThat(movement.getSaleId()).isNull();
        assertThat(movement.getTransferId()).isNull();
        assertThat(movement.getAdjustmentId()).isNull();
    }
}
