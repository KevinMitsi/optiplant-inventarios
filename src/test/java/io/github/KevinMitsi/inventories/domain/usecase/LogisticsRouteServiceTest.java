package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateLogisticsRouteCommand;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.LogisticsRouteRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LogisticsRouteUseCase")
class LogisticsRouteServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID ORIGIN_ID = UUID.randomUUID();
    private static final UUID DESTINATION_ID = UUID.randomUUID();

    @Mock
    private LogisticsRouteRepositoryPort routeRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;
    @Mock
    private BranchRepositoryPort branchRepository;

    private LogisticsRouteUseCase service;

    @BeforeEach
    void setUp() {
        service = new LogisticsRouteUseCase(routeRepository, organizationRepository, branchRepository);
        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(branchRepository.existsById(ORIGIN_ID)).thenReturn(true);
        when(branchRepository.existsById(DESTINATION_ID)).thenReturn(true);
        when(routeRepository.save(any(LogisticsRoute.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("crea la ruta cuando el par origen/destino está libre")
    void createsRoute() {
        when(routeRepository.existsByOriginAndDestination(ORIGIN_ID, DESTINATION_ID)).thenReturn(false);

        LogisticsRoute route = service.createRoute(new CreateLogisticsRouteCommand(
                ORGANIZATION_ID, ORIGIN_ID, DESTINATION_ID, "Ruta Norte", 120, BigDecimal.TEN, (short) 1));

        assertThat(route.getEstimatedDurationMinutes()).isEqualTo(120);
        assertThat(route.isActive()).isTrue();
    }

    @Test
    @DisplayName("RN-07: origen y destino deben ser sucursales distintas")
    void rejectsSameOriginAndDestination() {
        assertThatThrownBy(() -> service.createRoute(new CreateLogisticsRouteCommand(
                ORGANIZATION_ID, ORIGIN_ID, ORIGIN_ID, "Ruta inválida", 60, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("falla si ya existe una ruta para ese origen/destino")
    void failsOnDuplicateRoute() {
        when(routeRepository.existsByOriginAndDestination(ORIGIN_ID, DESTINATION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.createRoute(new CreateLogisticsRouteCommand(
                ORGANIZATION_ID, ORIGIN_ID, DESTINATION_ID, "Ruta Norte", 120, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("falla si la sucursal de origen no existe")
    void failsWhenOriginMissing() {
        when(branchRepository.existsById(ORIGIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createRoute(new CreateLogisticsRouteCommand(
                ORGANIZATION_ID, ORIGIN_ID, DESTINATION_ID, "Ruta Norte", 120, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
