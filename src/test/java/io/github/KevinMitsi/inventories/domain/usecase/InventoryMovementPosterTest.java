package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.port.out.InventoryAlertRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryMovementRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.InsufficientStockException;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlertStatus;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlertType;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el invariante central del dominio (RN-04), el cálculo de costo promedio ponderado
 * (RF-23) y la apertura/resolución de alertas de reabastecimiento (§34) — la deuda técnica
 * que la Fase 4 no podía saldar porque este componente todavía no existía.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InventoryMovementPoster")
class InventoryMovementPosterTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private InventoryRepositoryPort inventoryRepository;
    @Mock
    private InventoryMovementRepositoryPort movementRepository;
    @Mock
    private InventoryAlertRepositoryPort alertRepository;

    private InventoryMovementPoster poster;

    @BeforeEach
    void setUp() {
        poster = new InventoryMovementPoster(inventoryRepository, movementRepository, alertRepository);

        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(call -> call.getArgument(0));
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(call -> call.getArgument(0));
        when(alertRepository.save(any(InventoryAlert.class))).thenAnswer(call -> call.getArgument(0));
        when(alertRepository.findOpenByInventoryId(any())).thenReturn(Optional.empty());
    }

    private PostInventoryMovementCommand purchase(BigDecimal quantity, BigDecimal unitCost) {
        return new PostInventoryMovementCommand(BRANCH_ID, PRODUCT_ID, "SKU-1", InventoryMovementType.PURCHASE_IN,
                quantity, unitCost, "Compra a proveedor", USER_ID, Instant.now(), UUID.randomUUID(), null, null, null);
    }

    private PostInventoryMovementCommand transferIn(BigDecimal quantity, BigDecimal unitCost) {
        return new PostInventoryMovementCommand(BRANCH_ID, PRODUCT_ID, "SKU-1", InventoryMovementType.TRANSFER_IN,
                quantity, unitCost, "Transferencia TR-0001", USER_ID, Instant.now(), null, null, UUID.randomUUID(), null);
    }

    private PostInventoryMovementCommand saleOut(BigDecimal quantity) {
        return PostInventoryMovementCommand.withoutReference(BRANCH_ID, PRODUCT_ID, "SKU-1",
                InventoryMovementType.SALE_OUT, quantity, "Venta", USER_ID);
    }

    @Nested
    @DisplayName("Invariante RN-04: todo cambio de saldo deja un movimiento")
    class MovementInvariant {

        @Test
        @DisplayName("una compra crea el saldo si no existía y postea el movimiento")
        void createsInventoryOnFirstMovement() {
            // Arrange
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.empty());

            // Act
            InventoryMovement movement = poster.post(purchase(new BigDecimal("10"), new BigDecimal("50.00")));

            // Assert
            verify(inventoryRepository).save(any(Inventory.class));
            verify(movementRepository).save(any(InventoryMovement.class));
            assertThat(movement.getMovementType()).isEqualTo(InventoryMovementType.PURCHASE_IN);
            assertThat(movement.getQuantity()).isEqualTo(Quantity.of("10"));
        }

        @Test
        @DisplayName("no hay movimiento sin actualización previa del saldo: si el saldo falla, no se postea nada")
        void insufficientStockPreventsMovement() {
            // Arrange
            Inventory existing = Inventory.open(BRANCH_ID, PRODUCT_ID);
            existing.increase(Quantity.of("2"));
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));

            // Act & Assert
            assertThatThrownBy(() -> poster.post(saleOut(new BigDecimal("5"))))
                    .isInstanceOf(InsufficientStockException.class);

            verify(inventoryRepository, never()).save(any());
            verify(movementRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Costo promedio ponderado (RF-23, HU-21)")
    class WeightedAverageCost {

        @Test
        @DisplayName("una compra sobre saldo existente recalcula el costo ponderado por cantidad")
        void recalculatesWeightedAverage() {
            // Arrange: saldo previo 10 unidades a 100
            Inventory existing = Inventory.open(BRANCH_ID, PRODUCT_ID);
            existing.receiveWithCost(Quantity.of("10"), Money.of("100.00"));
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));

            ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);

            // Act: compra de 10 más a 200 -> (10*100 + 10*200) / 20 = 150
            InventoryMovement movement = poster.post(purchase(new BigDecimal("10"), new BigDecimal("200.00")));

            // Assert
            verify(inventoryRepository).save(captor.capture());
            assertThat(captor.getValue().getAverageCost().amount())
                    .isEqualByComparingTo(new BigDecimal("150.0000"));
            assertThat(movement.getUnitCost().amount()).isEqualByComparingTo(new BigDecimal("200.0000"));
        }

        @Test
        @DisplayName("recibir una transferencia también recalcula el costo ponderado, con el costo heredado del origen")
        void transferInAlsoRecalculatesWeightedAverage() {
            // Arrange: saldo previo en destino, 10 unidades a 100
            Inventory existing = Inventory.open(BRANCH_ID, PRODUCT_ID);
            existing.receiveWithCost(Quantity.of("10"), Money.of("100.00"));
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));

            ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);

            // Act: llegan 10 más, con el costo que traía el origen (200) -> (10*100 + 10*200) / 20 = 150
            InventoryMovement movement = poster.post(transferIn(new BigDecimal("10"), new BigDecimal("200.00")));

            // Assert
            verify(inventoryRepository).save(captor.capture());
            assertThat(captor.getValue().getAverageCost().amount())
                    .isEqualByComparingTo(new BigDecimal("150.0000"));
            assertThat(movement.getUnitCost().amount()).isEqualByComparingTo(new BigDecimal("200.0000"));
        }

        @Test
        @DisplayName("una venta no registra costo unitario en el movimiento")
        void saleMovementHasNoUnitCost() {
            // Arrange
            Inventory existing = Inventory.open(BRANCH_ID, PRODUCT_ID);
            existing.increase(Quantity.of("10"));
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));

            // Act
            InventoryMovement movement = poster.post(saleOut(new BigDecimal("3")));

            // Assert
            assertThat(movement.getUnitCost()).isNull();
        }
    }

    @Nested
    @DisplayName("Alertas de reabastecimiento (§34)")
    class Alerts {

        @Test
        @DisplayName("una salida que agota el saldo abre una alerta OUT_OF_STOCK")
        void opensOutOfStockAlert() {
            // Arrange
            Inventory existing = Inventory.open(BRANCH_ID, PRODUCT_ID);
            existing.increase(Quantity.of("5"));
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));

            ArgumentCaptor<InventoryAlert> captor = ArgumentCaptor.forClass(InventoryAlert.class);

            // Act
            poster.post(saleOut(new BigDecimal("5")));

            // Assert
            verify(alertRepository).save(captor.capture());
            assertThat(captor.getValue().getAlertType()).isEqualTo(InventoryAlertType.OUT_OF_STOCK);
            assertThat(captor.getValue().getStatus()).isEqualTo(InventoryAlertStatus.OPEN);
        }

        @Test
        @DisplayName("una entrada que supera el mínimo resuelve la alerta abierta")
        void resolvesAlertWhenStockRecovers() {
            // Arrange
            Inventory existing = Inventory.open(BRANCH_ID, PRODUCT_ID);
            existing.setMinimumStock(Quantity.of("5"));
            existing.increase(Quantity.of("1"));
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));

            InventoryAlert openAlert = InventoryAlert.open(existing.getId(), InventoryAlertType.LOW_STOCK,
                    Quantity.of("1"), Quantity.of("5"));
            when(alertRepository.findOpenByInventoryId(existing.getId())).thenReturn(Optional.of(openAlert));

            ArgumentCaptor<InventoryAlert> captor = ArgumentCaptor.forClass(InventoryAlert.class);

            // Act: entra suficiente para superar el mínimo
            poster.post(PostInventoryMovementCommand.withoutReference(BRANCH_ID, PRODUCT_ID, "SKU-1",
                    InventoryMovementType.RETURN_IN, new BigDecimal("10"), "Devolución", USER_ID));

            // Assert
            verify(alertRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(InventoryAlertStatus.RESOLVED);
        }

        @Test
        @DisplayName("mientras el saldo siga sano, no se toca la tabla de alertas")
        void doesNotTouchAlertsWhenStockIsHealthy() {
            // Arrange
            Inventory existing = Inventory.open(BRANCH_ID, PRODUCT_ID);
            existing.setMinimumStock(Quantity.of("5"));
            existing.increase(Quantity.of("50"));
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));

            // Act
            poster.post(saleOut(new BigDecimal("1")));

            // Assert
            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("si ya hay una alerta abierta del mismo tipo, no se abre otra")
        void doesNotDuplicateSameTypeAlert() {
            // Arrange
            Inventory existing = Inventory.open(BRANCH_ID, PRODUCT_ID);
            existing.setMinimumStock(Quantity.of("10"));
            existing.increase(Quantity.of("8"));
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));

            InventoryAlert openAlert = InventoryAlert.open(existing.getId(), InventoryAlertType.LOW_STOCK,
                    Quantity.of("8"), Quantity.of("10"));
            when(alertRepository.findOpenByInventoryId(existing.getId())).thenReturn(Optional.of(openAlert));

            // Act: sigue bajo, sigue siendo LOW_STOCK
            poster.post(PostInventoryMovementCommand.withoutReference(BRANCH_ID, PRODUCT_ID, "SKU-1",
                    InventoryMovementType.RETURN_IN, new BigDecimal("1"), "Ajuste menor", USER_ID));

            // Assert
            verify(alertRepository, times(0)).save(any());
        }
    }
}
