package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.out.DashboardRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.BranchComparison;
import io.github.KevinMitsi.inventories.domain.model.ProductRotation;
import io.github.KevinMitsi.inventories.domain.model.SalesSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DashboardUseCase")
class DashboardUseCaseTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");

    @Mock
    private DashboardRepositoryPort dashboardRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;

    private DashboardUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DashboardUseCase(dashboardRepository, organizationRepository);
        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("RF-42/43: delega el volumen de ventas mensual una vez validada la organización")
    void returnsSalesSummary() {
        SalesSummary summary = new SalesSummary(BRANCH_ID, "Sucursal Norte", 2026, 6, 3L, BigDecimal.valueOf(150));
        when(dashboardRepository.getSalesSummary(ORGANIZATION_ID, BRANCH_ID, FROM, TO)).thenReturn(List.of(summary));

        List<SalesSummary> result = useCase.getSalesSummary(ORGANIZATION_ID, BRANCH_ID, FROM, TO);

        assertThat(result).containsExactly(summary);
    }

    @Test
    @DisplayName("RF-44: delega la rotación de productos una vez validada la organización")
    void returnsProductRotation() {
        ProductRotation rotation = new ProductRotation(UUID.randomUUID(), "Fertilizante", BigDecimal.TEN, 2L);
        when(dashboardRepository.getProductRotation(ORGANIZATION_ID, null, FROM, TO)).thenReturn(List.of(rotation));

        List<ProductRotation> result = useCase.getProductRotation(ORGANIZATION_ID, null, FROM, TO);

        assertThat(result).containsExactly(rotation);
    }

    @Test
    @DisplayName("RF-47: delega la comparación entre sucursales una vez validada la organización")
    void returnsBranchComparison() {
        BranchComparison comparison = new BranchComparison(
                BRANCH_ID, "Sucursal Norte", 5L, BigDecimal.valueOf(500), BigDecimal.valueOf(2000), 1L);
        when(dashboardRepository.getBranchComparison(ORGANIZATION_ID)).thenReturn(List.of(comparison));

        List<BranchComparison> result = useCase.getBranchComparison(ORGANIZATION_ID);

        assertThat(result).containsExactly(comparison);
    }

    @Test
    @DisplayName("falla si la organización no existe")
    void failsWhenOrganizationMissing() {
        UUID unknownOrg = UUID.randomUUID();
        when(organizationRepository.existsById(unknownOrg)).thenReturn(false);

        assertThatThrownBy(() -> useCase.getSalesSummary(unknownOrg, null, FROM, TO))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> useCase.getProductRotation(unknownOrg, null, FROM, TO))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> useCase.getBranchComparison(unknownOrg))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
