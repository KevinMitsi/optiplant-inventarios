package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InventoryAdjustment (ajuste formal de inventario, §18)")
class InventoryAdjustmentTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();

    private List<InventoryAdjustmentItem> oneItem() {
        return List.of(InventoryAdjustmentItem.create(UUID.randomUUID(), new BigDecimal("-3"), null));
    }

    @Test
    @DisplayName("nace sin aprobar")
    void createsUnapproved() {
        // Act
        InventoryAdjustment adjustment = InventoryAdjustment.create(BRANCH_ID, CREATED_BY, "Conteo físico", oneItem());

        // Assert
        assertThat(adjustment.isApproved()).isFalse();
        assertThat(adjustment.getApprovedBy()).isNull();
    }

    @Test
    @DisplayName("rechaza un ajuste sin líneas")
    void rejectsEmptyItems() {
        assertThatThrownBy(() -> InventoryAdjustment.create(BRANCH_ID, CREATED_BY, "Conteo físico", List.of()))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("aprobar fija responsable y fecha")
    void approveSetsApprover() {
        // Arrange
        InventoryAdjustment adjustment = InventoryAdjustment.create(BRANCH_ID, CREATED_BY, "Conteo físico", oneItem());
        UUID approver = UUID.randomUUID();

        // Act
        adjustment.approve(approver);

        // Assert
        assertThat(adjustment.isApproved()).isTrue();
        assertThat(adjustment.getApprovedBy()).isEqualTo(approver);
        assertThat(adjustment.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("no se puede aprobar dos veces")
    void cannotApproveTwice() {
        // Arrange
        InventoryAdjustment adjustment = InventoryAdjustment.create(BRANCH_ID, CREATED_BY, "Conteo físico", oneItem());
        adjustment.approve(UUID.randomUUID());

        // Act & Assert
        assertThatThrownBy(() -> adjustment.approve(UUID.randomUUID()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("una línea con cantidad positiva es de entrada")
    void positiveDeltaIsEntry() {
        InventoryAdjustmentItem item = InventoryAdjustmentItem.create(UUID.randomUUID(), new BigDecimal("5"), null);

        assertThat(item.isEntry()).isTrue();
        assertThat(item.absoluteQuantity()).isEqualTo(Quantity.of("5"));
    }

    @Test
    @DisplayName("una línea con cantidad negativa es de salida y su magnitud es absoluta")
    void negativeDeltaIsExit() {
        InventoryAdjustmentItem item = InventoryAdjustmentItem.create(UUID.randomUUID(), new BigDecimal("-5"), null);

        assertThat(item.isEntry()).isFalse();
        assertThat(item.absoluteQuantity()).isEqualTo(Quantity.of("5"));
    }

    @Test
    @DisplayName("una línea con cantidad cero es inválida")
    void rejectsZeroDelta() {
        assertThatThrownBy(() -> InventoryAdjustmentItem.create(UUID.randomUUID(), BigDecimal.ZERO, null))
                .isInstanceOf(DomainValidationException.class);
    }
}
