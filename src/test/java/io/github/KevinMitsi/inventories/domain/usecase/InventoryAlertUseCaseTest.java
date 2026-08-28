package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAlertRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;
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

    @Mock
    private InventoryAlertRepositoryPort alertRepository;

    private InventoryAlertUseCase service;
    private InventoryAlert alert;

    @BeforeEach
    void setUp() {
        service = new InventoryAlertUseCase(alertRepository);
        alert = InventoryAlert.open(UUID.randomUUID(), InventoryAlertType.LOW_STOCK,
                Quantity.of("2"), Quantity.of("10"));

        when(alertRepository.save(any(InventoryAlert.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("resolver una alerta abierta la marca RESOLVED")
    void resolvesOpenAlert() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));

        InventoryAlert result = service.resolveAlert(ALERT_ID);

        assertThat(result.getStatus()).isEqualTo(InventoryAlertStatus.RESOLVED);
    }

    @Test
    @DisplayName("descartar una alerta abierta la marca DISMISSED")
    void dismissesOpenAlert() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));

        InventoryAlert result = service.dismissAlert(ALERT_ID);

        assertThat(result.getStatus()).isEqualTo(InventoryAlertStatus.DISMISSED);
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
}
