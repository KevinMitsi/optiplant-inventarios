package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.port.in.command.ResolveTransferIssueCommand;
import io.github.KevinMitsi.inventories.application.port.out.TransferIssueRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.Transfer;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;
import io.github.KevinMitsi.inventories.domain.model.TransferIssueType;
import io.github.KevinMitsi.inventories.domain.model.TransferItem;
import io.github.KevinMitsi.inventories.domain.model.TransferPriority;
import io.github.KevinMitsi.inventories.domain.model.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cubre HU-33: resolver la última incidencia pendiente cierra la transferencia. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransferIssueUseCase")
class TransferIssueServiceTest {

    private static final UUID RESOLVED_BY = UUID.randomUUID();

    @Mock
    private TransferIssueRepositoryPort transferIssueRepository;
    @Mock
    private TransferRepositoryPort transferRepository;

    private TransferIssueUseCase service;
    private Transfer transfer;
    private TransferItem item;
    private TransferIssue issue;

    @BeforeEach
    void setUp() {
        service = new TransferIssueUseCase(transferIssueRepository, transferRepository);

        item = TransferItem.create(UUID.randomUUID(), Quantity.of("10"));
        transfer = Transfer.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TR-0001",
                TransferPriority.NORMAL, null, List.of(item));
        transfer.approve(UUID.randomUUID(), Map.of());
        transfer.startPreparation();
        transfer.dispatch(Map.of());
        transfer.receive(Map.of(item.getId(), Quantity.of("6")));

        issue = TransferIssue.report(item.getId(), TransferIssueType.MISSING, Quantity.of("4"), null,
                UUID.randomUUID());

        when(transferIssueRepository.findById(issue.getId())).thenReturn(Optional.of(issue));
        when(transferIssueRepository.save(any(TransferIssue.class))).thenAnswer(call -> call.getArgument(0));
        when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    @DisplayName("Resolución")
    class Resolution {

        @Test
        @DisplayName("resolver la última incidencia pendiente cierra la transferencia")
        void resolvingLastPendingIssueClosesTransfer() {
            when(transferIssueRepository.existsUnresolvedByTransferItemIdIn(List.of(item.getId())))
                    .thenReturn(false);

            TransferIssue resolved = service.resolveIssue(
                    new ResolveTransferIssueCommand(transfer.getId(), issue.getId(), RESOLVED_BY, "ADJUSTMENT"));

            assertThat(resolved.isResolved()).isTrue();
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CLOSED);
            verify(transferRepository).save(transfer);
        }

        @Test
        @DisplayName("si quedan incidencias pendientes, la transferencia no se cierra")
        void doesNotCloseWhileIssuesRemainUnresolved() {
            when(transferIssueRepository.existsUnresolvedByTransferItemIdIn(List.of(item.getId())))
                    .thenReturn(true);

            service.resolveIssue(new ResolveTransferIssueCommand(transfer.getId(), issue.getId(), RESOLVED_BY,
                    "ADJUSTMENT"));

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PARTIALLY_RECEIVED);
        }
    }
}
