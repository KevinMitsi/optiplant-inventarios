package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateSupplierCommand;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.SupplierRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Supplier;
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
@DisplayName("SupplierService")
class SupplierServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Mock
    private SupplierRepositoryPort supplierRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;

    private SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(supplierRepository, organizationRepository);
        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("crea el proveedor cuando el código está libre")
    void createsSupplier() {
        when(supplierRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "PROV-01")).thenReturn(false);

        Supplier supplier = service.createSupplier(new CreateSupplierCommand(
                ORGANIZATION_ID, "prov-01", "Distribuidora Andina", null, null, null));

        assertThat(supplier.getCode()).isEqualTo("PROV-01");
        assertThat(supplier.isActive()).isTrue();
    }

    @Test
    @DisplayName("falla si el código de proveedor ya está en uso")
    void failsOnDuplicateCode() {
        when(supplierRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "PROV-01")).thenReturn(true);

        assertThatThrownBy(() -> service.createSupplier(new CreateSupplierCommand(
                ORGANIZATION_ID, "PROV-01", "Distribuidora Andina", null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("falla si la organización no existe")
    void failsWhenOrganizationMissing() {
        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createSupplier(new CreateSupplierCommand(
                ORGANIZATION_ID, "PROV-01", "Distribuidora Andina", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
