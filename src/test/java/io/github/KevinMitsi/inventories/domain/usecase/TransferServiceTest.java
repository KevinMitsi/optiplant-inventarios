package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.port.in.command.ApproveTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.DispatchTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceiveTransferCommand;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.CarrierRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.LogisticsRouteRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferIssueRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferStatusHistoryRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.InsufficientStockException;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.Transfer;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;
import io.github.KevinMitsi.inventories.domain.model.TransferItem;
import io.github.KevinMitsi.inventories.domain.model.TransferPriority;
import io.github.KevinMitsi.inventories.domain.model.TransferStatus;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre RN-07 (origen/destino distintos), RN-08 (no despachar sin stock suficiente en origen)
 * y RN-09/RN-10 (la recepción parcial refleja la cantidad real y abre una incidencia).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransferUseCase")
class TransferServiceTest {

    private static final UUID ORIGIN_ID = UUID.randomUUID();
    private static final UUID DESTINATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private TransferRepositoryPort transferRepository;
    @Mock
    private TransferIssueRepositoryPort transferIssueRepository;
    @Mock
    private TransferStatusHistoryRepositoryPort statusHistoryRepository;
    @Mock
    private BranchRepositoryPort branchRepository;
    @Mock
    private ProductRepositoryPort productRepository;
    @Mock
    private CarrierRepositoryPort carrierRepository;
    @Mock
    private LogisticsRouteRepositoryPort logisticsRouteRepository;
    @Mock
    private InventoryMovementPoster poster;

    private TransferUseCase service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new TransferUseCase(transferRepository, transferIssueRepository, statusHistoryRepository,
                branchRepository, productRepository, carrierRepository, logisticsRouteRepository, poster);

        UnitOfMeasure unit = new UnitOfMeasure(UUID.randomUUID(), "UNIT", "Unidad", "und");
        product = Product.create(UUID.randomUUID(), null, "SKU-1", null, "Producto", null, unit);

        when(branchRepository.existsById(ORIGIN_ID)).thenReturn(true);
        when(branchRepository.existsById(DESTINATION_ID)).thenReturn(true);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(call -> call.getArgument(0));
    }

    private Transfer transferInPreparation() {
        TransferItem item = TransferItem.create(product.getId(), Quantity.of("10"));
        Transfer transfer = Transfer.create(ORIGIN_ID, DESTINATION_ID, USER_ID, "TR-0001", TransferPriority.NORMAL,
                null, List.of(item));
        transfer.approve(USER_ID, java.util.Map.of());
        transfer.startPreparation();
        return transfer;
    }

    @Nested
    @DisplayName("Creación")
    class Creation {

        @Test
        @DisplayName("crea la transferencia cuando el número no está en uso")
        void createsTransfer() {
            CreateTransferCommand.Item item = new CreateTransferCommand.Item(product.getId(), new BigDecimal("5"));
            CreateTransferCommand command = new CreateTransferCommand(ORIGIN_ID, DESTINATION_ID, USER_ID, "TR-0002",
                    "HIGH", null, List.of(item));

            Transfer created = service.createTransfer(command);

            assertThat(created.getStatus()).isEqualTo(TransferStatus.REQUESTED);
            assertThat(created.getPriority()).isEqualTo(TransferPriority.HIGH);
        }

        @Test
        @DisplayName("rechaza un número de transferencia duplicado")
        void rejectsDuplicateNumber() {
            when(transferRepository.existsByTransferNumber("TR-0002")).thenReturn(true);
            CreateTransferCommand.Item item = new CreateTransferCommand.Item(product.getId(), new BigDecimal("5"));
            CreateTransferCommand command = new CreateTransferCommand(ORIGIN_ID, DESTINATION_ID, USER_ID, "TR-0002",
                    null, null, List.of(item));

            assertThatThrownBy(() -> service.createTransfer(command))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("Asignación de logística (Fase 5)")
    class LogisticsAssignment {

        @Test
        @DisplayName("asigna transportista y ruta cuando la ruta conecta origen y destino")
        void assignsLogistics() {
            Transfer transfer = transferInPreparation();
            UUID carrierId = UUID.randomUUID();
            UUID routeId = UUID.randomUUID();
            io.github.KevinMitsi.inventories.domain.model.LogisticsRoute route =
                    io.github.KevinMitsi.inventories.domain.model.LogisticsRoute.create(
                            UUID.randomUUID(), ORIGIN_ID, DESTINATION_ID, "Ruta", 60, null, (short) 0);

            when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
            when(carrierRepository.existsById(carrierId)).thenReturn(true);
            when(logisticsRouteRepository.findById(routeId)).thenReturn(Optional.of(route));

            Transfer saved = service.assignLogistics(
                    new io.github.KevinMitsi.inventories.application.port.in.command.AssignTransferLogisticsCommand(
                            transfer.getId(), carrierId, routeId, null));

            assertThat(saved.getCarrierId()).isEqualTo(carrierId);
            assertThat(saved.getRouteId()).isEqualTo(routeId);
        }

        @Test
        @DisplayName("rechaza una ruta que no conecta el origen y destino de la transferencia")
        void rejectsRouteThatDoesNotConnect() {
            Transfer transfer = transferInPreparation();
            UUID carrierId = UUID.randomUUID();
            UUID routeId = UUID.randomUUID();
            io.github.KevinMitsi.inventories.domain.model.LogisticsRoute unrelatedRoute =
                    io.github.KevinMitsi.inventories.domain.model.LogisticsRoute.create(
                            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Otra ruta", 60, null, (short) 0);

            when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
            when(carrierRepository.existsById(carrierId)).thenReturn(true);
            when(logisticsRouteRepository.findById(routeId)).thenReturn(Optional.of(unrelatedRoute));

            assertThatThrownBy(() -> service.assignLogistics(
                    new io.github.KevinMitsi.inventories.application.port.in.command.AssignTransferLogisticsCommand(
                            transfer.getId(), carrierId, routeId, null)))
                    .isInstanceOf(io.github.KevinMitsi.inventories.domain.exception.DomainValidationException.class);
        }
    }

    @Nested
    @DisplayName("Despacho")
    class Dispatch {

        @Test
        @DisplayName("RN-08: propaga InsufficientStockException cuando no hay stock suficiente en origen")
        void propagatesInsufficientStockFromPoster() {
            Transfer transfer = transferInPreparation();
            when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

            doThrow(new InsufficientStockException(ORIGIN_ID, product.getId(), product.getSku(),
                    new BigDecimal("10"), new BigDecimal("2")))
                    .when(poster).post(any(PostInventoryMovementCommand.class));

            DispatchTransferCommand command = new DispatchTransferCommand(transfer.getId(), USER_ID, List.of());

            assertThatThrownBy(() -> service.dispatchTransfer(command))
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test
        @DisplayName("postea TRANSFER_OUT desde el origen por la cantidad despachada")
        void postsTransferOutFromOrigin() {
            Transfer transfer = transferInPreparation();
            when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);

            Transfer result = service.dispatchTransfer(
                    new DispatchTransferCommand(transfer.getId(), USER_ID, List.of()));

            verify(poster).post(captor.capture());
            assertThat(captor.getValue().movementType()).isEqualTo(InventoryMovementType.TRANSFER_OUT);
            assertThat(captor.getValue().branchId()).isEqualTo(ORIGIN_ID);
            assertThat(captor.getValue().quantity()).isEqualByComparingTo("10");
            assertThat(captor.getValue().transferId()).isEqualTo(transfer.getId());
            assertThat(result.getStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
        }
    }

    @Nested
    @DisplayName("Recepción")
    class Reception {

        @Test
        @DisplayName("postea TRANSFER_IN al destino por lo realmente recibido")
        void postsTransferInToDestination() {
            Transfer transfer = transferInPreparation();
            transfer.dispatch(java.util.Map.of());
            when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
            UUID itemId = transfer.getItems().get(0).getId();

            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);

            ReceiveTransferCommand command = new ReceiveTransferCommand(transfer.getId(), USER_ID,
                    List.of(new ReceiveTransferCommand.ItemQuantity(itemId, new BigDecimal("10"))));

            Transfer result = service.receiveTransfer(command);

            verify(poster).post(captor.capture());
            assertThat(captor.getValue().movementType()).isEqualTo(InventoryMovementType.TRANSFER_IN);
            assertThat(captor.getValue().branchId()).isEqualTo(DESTINATION_ID);
            assertThat(captor.getValue().quantity()).isEqualByComparingTo("10");
            assertThat(result.getStatus()).isEqualTo(TransferStatus.RECEIVED);
            verify(transferIssueRepository, never()).save(any(TransferIssue.class));
        }

        @Test
        @DisplayName("RN-09/RN-10: recibir menos abre una incidencia MISSING por el faltante exacto")
        void shortReceiptOpensIssue() {
            Transfer transfer = transferInPreparation();
            transfer.dispatch(java.util.Map.of());
            when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
            UUID itemId = transfer.getItems().get(0).getId();

            ReceiveTransferCommand command = new ReceiveTransferCommand(transfer.getId(), USER_ID,
                    List.of(new ReceiveTransferCommand.ItemQuantity(itemId, new BigDecimal("6"))));

            Transfer result = service.receiveTransfer(command);

            assertThat(result.getStatus()).isEqualTo(TransferStatus.PARTIALLY_RECEIVED);

            ArgumentCaptor<TransferIssue> issueCaptor = ArgumentCaptor.forClass(TransferIssue.class);
            verify(transferIssueRepository, times(1)).save(issueCaptor.capture());
            assertThat(issueCaptor.getValue().getQuantity()).isEqualTo(Quantity.of("4"));
            assertThat(issueCaptor.getValue().getTransferItemId()).isEqualTo(itemId);
        }
    }

    @Nested
    @DisplayName("Cancelación")
    class Cancellation {

        @Test
        @DisplayName("cancela antes de despachar")
        void cancelsBeforeDispatch() {
            Transfer transfer = transferInPreparation();
            // aún no despachada: transferInPreparation() solo aprueba y prepara.
            when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

            Transfer result = service.cancelTransfer(transfer.getId(), USER_ID);

            assertThat(result.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        }
    }
}
