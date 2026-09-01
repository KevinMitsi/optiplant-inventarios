package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Crea un ajuste de inventario en borrador, con sus líneas (ENTITIES.md §18). */
public record CreateInventoryAdjustmentCommand(UUID branchId, UUID createdBy, String reason,
                                                List<Item> items) {

    /** @param quantityDelta con signo: positivo entra, negativo sale */
    public record Item(UUID productId, BigDecimal quantityDelta, String reason) {
    }
}
