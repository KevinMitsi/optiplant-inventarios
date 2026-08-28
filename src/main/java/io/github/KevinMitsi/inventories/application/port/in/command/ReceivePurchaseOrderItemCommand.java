package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.UUID;

/** Confirma la recepción de una línea de compra, total o parcial (HU-19, RF-21). */
public record ReceivePurchaseOrderItemCommand(UUID purchaseOrderId, UUID itemId, BigDecimal quantityReceived,
                                              UUID userId) {
}
