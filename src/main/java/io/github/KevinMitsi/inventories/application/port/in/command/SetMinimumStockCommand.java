package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.UUID;

/** Configura el stock mínimo de un producto en una sucursal (HU-15, RF-15). */
public record SetMinimumStockCommand(UUID branchId, UUID productId, BigDecimal minimumStock) {
}
