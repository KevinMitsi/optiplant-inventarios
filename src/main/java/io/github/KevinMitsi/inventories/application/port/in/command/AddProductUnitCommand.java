package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Anade una presentacion al producto (HU-10, RF-09).
 *
 * @param conversionFactor cuantas unidades base equivale una de esta presentacion
 */
public record AddProductUnitCommand(UUID productId,
                                    UUID unitOfMeasureId,
                                    BigDecimal conversionFactor) {
}
