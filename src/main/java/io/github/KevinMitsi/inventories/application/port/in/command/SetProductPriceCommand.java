package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.UUID;

/** Fija (crea o reemplaza) el precio de un producto/presentación en una lista de precios. */
public record SetProductPriceCommand(UUID priceListId, UUID productId, UUID productUnitId, BigDecimal price) {
}
