package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageTransferIssueUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryTransferIssueUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.ResolveTransferIssueCommand;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;
import io.github.KevinMitsi.inventories.domain.usecase.TransferIssueUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class TransferIssueService implements ManageTransferIssueUseCase, QueryTransferIssueUseCase {

    private final TransferIssueUseCase useCase;

    public TransferIssueService(TransferIssueUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public TransferIssue resolveIssue(ResolveTransferIssueCommand command) {
        return useCase.resolveIssue(command);
    }

    @Override
    public TransferIssue getIssueById(UUID issueId) {
        return useCase.getIssueById(issueId);
    }

    @Override
    public List<TransferIssue> listIssuesForTransfer(UUID transferId) {
        return useCase.listIssuesForTransfer(transferId);
    }
}
