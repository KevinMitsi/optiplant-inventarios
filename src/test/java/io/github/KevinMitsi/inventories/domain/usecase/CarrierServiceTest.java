package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateCarrierCommand;
import io.github.KevinMitsi.inventories.application.port.out.CarrierRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Carrier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CarrierUseCase")
class CarrierServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Mock
    private CarrierRepositoryPort carrierRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;

    private CarrierUseCase service;

    @BeforeEach
    void setUp() {
        service = new CarrierUseCase(carrierRepository, organizationRepository);
        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(carrierRepository.save(any(Carrier.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("crea el transportista cuando el código está libre")
    void createsCarrier() {
        when(carrierRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "TRANS-01")).thenReturn(false);

        Carrier carrier = service.createCarrier(new CreateCarrierCommand(
                ORGANIZATION_ID, "trans-01", "Transportes Rápidos", null, null));

        assertThat(carrier.getCode()).isEqualTo("TRANS-01");
        assertThat(carrier.isActive()).isTrue();
    }

    @Test
    @DisplayName("falla si el código de transportista ya está en uso")
    void failsOnDuplicateCode() {
        when(carrierRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "TRANS-01")).thenReturn(true);

        assertThatThrownBy(() -> service.createCarrier(new CreateCarrierCommand(
                ORGANIZATION_ID, "TRANS-01", "Transportes Rápidos", null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("falla si la organización no existe")
    void failsWhenOrganizationMissing() {
        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createCarrier(new CreateCarrierCommand(
                ORGANIZATION_ID, "TRANS-01", "Transportes Rápidos", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
