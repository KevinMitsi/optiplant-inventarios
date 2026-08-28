package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.ResolveTransferIssueCommand;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;

public interface ManageTransferIssueUseCase {

    /**
     * Resuelve una incidencia (HU-33). Si era la última pendiente de su transferencia, la
     * transferencia pasa a {@code CLOSED}.
     */
    TransferIssue resolveIssue(ResolveTransferIssueCommand command);
}
