package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageTransferUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryTransferUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.ApproveTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.AssignTransferLogisticsCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.DispatchTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceiveTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.TransferSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Transfer;
import io.github.KevinMitsi.inventories.domain.usecase.TransferUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Primary
@Service
@Transactional(rollbackFor = Exception.class)
public class TransferService implements ManageTransferUseCase, QueryTransferUseCase {

    private final TransferUseCase useCase;

    public TransferService(TransferUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Transfer createTransfer(CreateTransferCommand command) {
        return useCase.createTransfer(command);
    }

    @Override
    public Transfer approveTransfer(ApproveTransferCommand command) {
        return useCase.approveTransfer(command);
    }

    @Override
    public Transfer assignLogistics(AssignTransferLogisticsCommand command) {
        return useCase.assignLogistics(command);
    }

    @Override
    public Transfer startPreparation(UUID transferId, UUID userId) {
        return useCase.startPreparation(transferId, userId);
    }

    @Override
    public Transfer dispatchTransfer(DispatchTransferCommand command) {
        return useCase.dispatchTransfer(command);
    }

    @Override
    public Transfer receiveTransfer(ReceiveTransferCommand command) {
        return useCase.receiveTransfer(command);
    }

    @Override
    public Transfer cancelTransfer(UUID transferId, UUID userId) {
        return useCase.cancelTransfer(transferId, userId);
    }

    @Override
    public Transfer getTransferById(UUID transferId) {
        return useCase.getTransferById(transferId);
    }

    @Override
    public PageResult<Transfer> searchTransfers(TransferSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchTransfers(criteria, pageQuery);
    }
}
