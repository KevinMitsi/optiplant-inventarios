package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.UUID;

public record ChangeProductUnitFactorCommand(UUID productId,
                                             UUID productUnitId,
                                             BigDecimal conversionFactor) {
}
