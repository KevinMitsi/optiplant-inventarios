package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Despacha una transferencia en preparación (RN-08). Una línea sin entrada en
 * {@code shippedQuantities} se despacha por la cantidad aprobada.
 */
public record DispatchTransferCommand(UUID transferId, UUID userId, List<ItemQuantity> shippedQuantities) {

    public record ItemQuantity(UUID itemId, BigDecimal quantity) {
    }
}
