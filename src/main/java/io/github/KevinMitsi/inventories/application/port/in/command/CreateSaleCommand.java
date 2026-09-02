package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Crea una venta en borrador, con sus líneas (HU-22).
 *
 * @param priceListId opcional; si una línea no trae {@code unitPrice}, se resuelve contra
 *                     esta lista (HU-25)
 */
public record CreateSaleCommand(UUID branchId, UUID createdBy, UUID priceListId, String saleNumber,
                                Instant saleDate, String notes, List<Item> items) {

    /** @param unitPrice si es nulo, se toma de la lista de precios de la venta */
    public record Item(UUID productId, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal discountPercentage) {
    }
}
