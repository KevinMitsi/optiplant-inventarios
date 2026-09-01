package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Confirma la recepción de una transferencia, total o parcial (RN-09). A diferencia de
 * aprobar/despachar, una línea sin entrada en {@code receivedQuantities} se recibe en cero:
 * no hay una cantidad "por defecto" razonable para lo que físicamente no llegó.
 */
public record ReceiveTransferCommand(UUID transferId, UUID userId, List<ItemQuantity> receivedQuantities) {

    public record ItemQuantity(UUID itemId, BigDecimal quantity) {
    }
}
