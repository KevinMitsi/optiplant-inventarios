package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.domain.model.TransferIssue;

import java.util.List;
import java.util.UUID;

public interface QueryTransferIssueUseCase {

    TransferIssue getIssueById(UUID issueId);

    /** Todas las incidencias de las líneas de una transferencia, resueltas o no. */
    List<TransferIssue> listIssuesForTransfer(UUID transferId);
}
