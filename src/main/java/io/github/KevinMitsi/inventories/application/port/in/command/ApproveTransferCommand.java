package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Aprueba una transferencia solicitada (HU-29). Una línea sin entrada en
 * {@code approvedQuantities} se aprueba tal como fue solicitada.
 */
public record ApproveTransferCommand(UUID transferId, UUID approvedBy, List<ItemQuantity> approvedQuantities) {

    public record ItemQuantity(UUID itemId, BigDecimal quantity) {
    }
}
