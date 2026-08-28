package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Registro manual de una salida de inventario (HU-13), sin documento de origen.
 *
 * <p>Siempre se postea como {@code LOSS_OUT}: las salidas con documento —venta,
 * transferencia, ajuste formal— tienen su propio flujo dedicado.
 *
 * @param quantity cantidad en la unidad base del producto
 */
public record RegisterInventoryExitCommand(UUID branchId, UUID productId, BigDecimal quantity,
                                            String reason, UUID userId) {
}
