package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.ApproveTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.AssignTransferLogisticsCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.DispatchTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceiveTransferCommand;
import io.github.KevinMitsi.inventories.domain.model.Transfer;

import java.util.UUID;

public interface ManageTransferUseCase {

    Transfer createTransfer(CreateTransferCommand command);

    Transfer approveTransfer(ApproveTransferCommand command);

    /** Asigna transportista y ruta (Fase 5): solo antes de despachar. */
    Transfer assignLogistics(AssignTransferLogisticsCommand command);

    Transfer startPreparation(UUID transferId, UUID userId);

    /** Descuenta inventario de origen mediante {@code TRANSFER_OUT}, validando stock (RN-08). */
    Transfer dispatchTransfer(DispatchTransferCommand command);

    /**
     * Aumenta inventario de destino mediante {@code TRANSFER_IN} por lo realmente recibido
     * (RN-09), y abre una {@code TransferIssue} por cada línea con faltante (RN-10).
     */
    Transfer receiveTransfer(ReceiveTransferCommand command);

    /** Solo antes de despachar: después ya hay stock de origen comprometido. */
    Transfer cancelTransfer(UUID transferId, UUID userId);
}
