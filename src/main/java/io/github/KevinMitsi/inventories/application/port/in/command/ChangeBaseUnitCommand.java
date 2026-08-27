package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Designa otra presentacion como unidad base.
 *
 * @param previousBaseNewFactor factor que pasa a tener la base anterior. Se exige porque su
 *                              equivalencia con la nueva base no es deducible por el sistema.
 */
public record ChangeBaseUnitCommand(UUID productId,
                                    UUID newBaseProductUnitId,
                                    BigDecimal previousBaseNewFactor) {
}
