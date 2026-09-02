package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.result.InventoryAlertDetail;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAlertRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlertStatus;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlertType;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InventoryAlertUseCase")
class InventoryAlertServiceTest {

    private static final UUID ALERT_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private InventoryAlertRepositoryPort alertRepository;
    @Mock
    private InventoryRepositoryPort inventoryRepository;

    private InventoryAlertUseCase service;
    private InventoryAlert alert;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        service = new InventoryAlertUseCase(alertRepository, inventoryRepository);
        alert = InventoryAlert.open(UUID.randomUUID(), InventoryAlertType.LOW_STOCK,
                Quantity.of("2"), Quantity.of("10"));
        inventory = Inventory.reconstitute(alert.getInventoryId(), BRANCH_ID, PRODUCT_ID,
                Quantity.of("2"), Quantity.of("10"), io.github.KevinMitsi.inventories.domain.model.Money.ZERO,
                java.time.Instant.now(), 0);

        when(alertRepository.save(any(InventoryAlert.class))).thenAnswer(call -> call.getArgument(0));
        when(inventoryRepository.findById(alert.getInventoryId())).thenReturn(Optional.of(inventory));
    }

    @Test
    @DisplayName("resolver una alerta abierta la marca RESOLVED y resuelve sucursal/producto")
    void resolvesOpenAlert() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));

        InventoryAlertDetail result = service.resolveAlert(ALERT_ID);

        assertThat(result.alert().getStatus()).isEqualTo(InventoryAlertStatus.RESOLVED);
        assertThat(result.branchId()).isEqualTo(BRANCH_ID);
        assertThat(result.productId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    @DisplayName("descartar una alerta abierta la marca DISMISSED")
    void dismissesOpenAlert() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));

        InventoryAlertDetail result = service.dismissAlert(ALERT_ID);

        assertThat(result.alert().getStatus()).isEqualTo(InventoryAlertStatus.DISMISSED);
    }

    @Test
    @DisplayName("resolver una alerta ya cerrada falla")
    void cannotResolveClosedAlert() {
        alert.dismiss();
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.resolveAlert(ALERT_ID))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("falla si la alerta no existe")
    void failsWhenAlertMissing() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAlert(ALERT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("falla si el saldo referenciado ya no existe")
    void failsWhenInventoryMissing() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(inventoryRepository.findById(alert.getInventoryId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAlert(ALERT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
