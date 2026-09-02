package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Transfer (transferencia entre sucursales, EP-07)")
class TransferTest {

    private static final UUID ORIGIN_ID = UUID.randomUUID();
    private static final UUID DESTINATION_ID = UUID.randomUUID();
    private static final UUID REQUESTED_BY = UUID.randomUUID();
    private static final UUID APPROVED_BY = UUID.randomUUID();

    private TransferItem item;

    @BeforeEach
    void setUp() {
        item = TransferItem.create(UUID.randomUUID(), Quantity.of("10"));
    }

    private Transfer newTransfer() {
        return Transfer.create(ORIGIN_ID, DESTINATION_ID, REQUESTED_BY, "TR-0001", TransferPriority.NORMAL, null,
                List.of(item));
    }

    @Nested
    @DisplayName("Creación")
    class Creation {

        @Test
        @DisplayName("nace REQUESTED")
        void createsAsRequested() {
            assertThat(newTransfer().getStatus()).isEqualTo(TransferStatus.REQUESTED);
        }

        @Test
        @DisplayName("RN-07: origen y destino deben ser sucursales distintas")
        void rejectsSameOriginAndDestination() {
            assertThatThrownBy(() -> Transfer.create(ORIGIN_ID, ORIGIN_ID, REQUESTED_BY, "TR-0002",
                    TransferPriority.NORMAL, null, List.of(item)))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("una transferencia sin líneas es inválida")
        void rejectsEmptyItems() {
            assertThatThrownBy(() -> Transfer.create(ORIGIN_ID, DESTINATION_ID, REQUESTED_BY, "TR-0003",
                    TransferPriority.NORMAL, null, List.of()))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    @DisplayName("Aprobación")
    class Approval {

        @Test
        @DisplayName("sin ajustes, aprueba cada línea tal como fue solicitada (HU-29)")
        void approvesAsRequestedByDefault() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of());

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.APPROVED);
            assertThat(transfer.getApprovedBy()).isEqualTo(APPROVED_BY);
            assertThat(transfer.findItemById(item.getId()).orElseThrow().getApprovedQuantity())
                    .isEqualTo(Quantity.of("10"));
        }

        @Test
        @DisplayName("puede ajustar la cantidad aprobada de una línea a la baja")
        void approvesWithAdjustedQuantity() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of(item.getId(), Quantity.of("6")));

            assertThat(transfer.findItemById(item.getId()).orElseThrow().getApprovedQuantity())
                    .isEqualTo(Quantity.of("6"));
        }

        @Test
        @DisplayName("no se puede aprobar dos veces")
        void cannotApproveTwice() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of());

            assertThatThrownBy(() -> transfer.approve(APPROVED_BY, Map.of()))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Asignación de logística (Fase 5)")
    class LogisticsAssignment {

        @Test
        @DisplayName("asigna transportista y ruta antes de despachar")
        void assignsCarrierAndRoute() {
            Transfer transfer = newTransfer();
            UUID carrierId = UUID.randomUUID();
            UUID routeId = UUID.randomUUID();

            transfer.assignLogistics(carrierId, routeId, null);

            assertThat(transfer.getCarrierId()).isEqualTo(carrierId);
            assertThat(transfer.getRouteId()).isEqualTo(routeId);
        }

        @Test
        @DisplayName("no se puede asignar logística tras despachar")
        void cannotAssignAfterDispatch() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of());
            transfer.startPreparation();
            transfer.dispatch(Map.of());

            assertThatThrownBy(() -> transfer.assignLogistics(UUID.randomUUID(), UUID.randomUUID(), null))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Despacho")
    class Dispatch {

        @Test
        @DisplayName("no se puede despachar sin pasar por preparación")
        void cannotDispatchBeforePreparation() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of());

            assertThatThrownBy(() -> transfer.dispatch(Map.of()))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("sin ajustes, despacha lo aprobado")
        void dispatchesApprovedQuantityByDefault() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of());
            transfer.startPreparation();
            transfer.dispatch(Map.of());

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
            assertThat(transfer.findItemById(item.getId()).orElseThrow().getShippedQuantity())
                    .isEqualTo(Quantity.of("10"));
        }
    }

    @Nested
    @DisplayName("Recepción")
    class Reception {

        private Transfer inTransitTransfer() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of());
            transfer.startPreparation();
            transfer.dispatch(Map.of());
            return transfer;
        }

        @Test
        @DisplayName("recibir todo lo despachado deja la transferencia RECEIVED, sin incidencias")
        void fullReceiptLeavesReceivedWithNoShortfall() {
            Transfer transfer = inTransitTransfer();

            List<TransferItem> shortfall = transfer.receive(Map.of(item.getId(), Quantity.of("10")));

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.RECEIVED);
            assertThat(shortfall).isEmpty();
        }

        @Test
        @DisplayName("RN-09/RN-10: recibir menos deja PARTIALLY_RECEIVED y reporta el faltante exacto")
        void partialReceiptReportsShortfall() {
            Transfer transfer = inTransitTransfer();

            List<TransferItem> shortfall = transfer.receive(Map.of(item.getId(), Quantity.of("7")));

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PARTIALLY_RECEIVED);
            assertThat(shortfall).hasSize(1);
            assertThat(shortfall.get(0).missingQuantity()).isEqualTo(Quantity.of("3"));
        }

        @Test
        @DisplayName("una línea ausente del mapa de recepción se considera recibida en cero")
        void missingEntryMeansNothingArrived() {
            Transfer transfer = inTransitTransfer();

            List<TransferItem> shortfall = transfer.receive(Map.of());

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PARTIALLY_RECEIVED);
            assertThat(shortfall.get(0).missingQuantity()).isEqualTo(Quantity.of("10"));
        }
    }

    @Nested
    @DisplayName("Cancelación")
    class Cancellation {

        @Test
        @DisplayName("se puede cancelar antes de despachar")
        void canCancelBeforeDispatch() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of());

            transfer.cancel();

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        @DisplayName("no se puede cancelar tras despachar: ya hay stock de origen comprometido")
        void cannotCancelAfterDispatch() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of());
            transfer.startPreparation();
            transfer.dispatch(Map.of());

            assertThatThrownBy(transfer::cancel).isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Cierre")
    class Closing {

        @Test
        @DisplayName("solo se puede cerrar una transferencia PARTIALLY_RECEIVED")
        void canOnlyCloseFromPartiallyReceived() {
            Transfer transfer = newTransfer();

            assertThatThrownBy(transfer::close).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("cierra una transferencia PARTIALLY_RECEIVED")
        void closesPartiallyReceivedTransfer() {
            Transfer transfer = newTransfer();
            transfer.approve(APPROVED_BY, Map.of());
            transfer.startPreparation();
            transfer.dispatch(Map.of());
            transfer.receive(Map.of(item.getId(), Quantity.of("7")));

            transfer.close();

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CLOSED);
        }
    }
}
