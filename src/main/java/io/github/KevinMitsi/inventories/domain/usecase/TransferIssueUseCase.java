package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageTransferIssueUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryTransferIssueUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.ResolveTransferIssueCommand;
import io.github.KevinMitsi.inventories.application.port.out.TransferIssueRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferRepositoryPort;
import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import io.github.KevinMitsi.inventories.domain.model.Transfer;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;
import io.github.KevinMitsi.inventories.domain.model.TransferIssueResolution;
import io.github.KevinMitsi.inventories.domain.model.TransferItem;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Resolución de incidencias de transferencia (HU-33). Depende de {@link TransferRepositoryPort}
 * además de {@link TransferIssueRepositoryPort} por una sola razón: al resolver la última
 * incidencia pendiente de una transferencia, esta cierra esa transferencia ({@code CLOSED}).
 */
@AuditedUseCase
public class TransferIssueUseCase implements ManageTransferIssueUseCase, QueryTransferIssueUseCase {

    private static final Logger log = Logger.getLogger(TransferIssueUseCase.class.getName());

    private static final String ISSUE = "la incidencia de transferencia";
    private static final String TRANSFER = "la transferencia";

    private final TransferIssueRepositoryPort transferIssueRepository;
    private final TransferRepositoryPort transferRepository;

    public TransferIssueUseCase(TransferIssueRepositoryPort transferIssueRepository,
                                TransferRepositoryPort transferRepository) {
        this.transferIssueRepository = transferIssueRepository;
        this.transferRepository = transferRepository;
    }

    @Override
    public TransferIssue resolveIssue(ResolveTransferIssueCommand command) {
        TransferIssue issue = transferIssueRepository.findById(command.issueId())
                .orElseThrow(() -> new ResourceNotFoundException(ISSUE, command.issueId()));

        issue.resolve(TransferIssueResolution.fromString(command.resolutionType()), command.resolvedBy());
        TransferIssue saved = transferIssueRepository.save(issue);

        Transfer transfer = transferRepository.findById(command.transferId())
                .orElseThrow(() -> new ResourceNotFoundException(TRANSFER, command.transferId()));
        List<UUID> itemIds = transfer.getItems().stream().map(TransferItem::getId).toList();

        if (!transferIssueRepository.existsUnresolvedByTransferItemIdIn(itemIds)) {
            transfer.close();
            transferRepository.save(transfer);
            log.info(() -> "Transferencia cerrada tras resolver su última incidencia: id=%s".formatted(transfer.getId()));
        }

        return saved;
    }

    @Override
    public TransferIssue getIssueById(UUID issueId) {
        return transferIssueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException(ISSUE, issueId));
    }

    @Override
    public List<TransferIssue> listIssuesForTransfer(UUID transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException(TRANSFER, transferId));
        List<UUID> itemIds = transfer.getItems().stream().map(TransferItem::getId).toList();
        return transferIssueRepository.findByTransferItemIdIn(itemIds);
    }
}
