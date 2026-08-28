package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Registro manual de una entrada de inventario (HU-12), sin documento de origen.
 *
 * <p>Siempre se postea como {@code RETURN_IN}: las entradas con documento —compra,
 * transferencia, ajuste formal— tienen su propio flujo dedicado, que es quien conoce el
 * costo, la referencia y la conversión de unidad que este registro libre no necesita.
 *
 * @param quantity cantidad en la unidad base del producto
 */
public record RegisterInventoryEntryCommand(UUID branchId, UUID productId, BigDecimal quantity,
                                             String reason, UUID userId) {
}
