package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Crea una orden de compra en borrador, con sus líneas (HU-17, HU-18). */
public record CreatePurchaseOrderCommand(UUID branchId, UUID supplierId, UUID createdBy, String orderNumber,
                                         LocalDate orderDate, int paymentTermDays, String notes,
                                         List<Item> items) {

    public record Item(UUID productId, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal discountPercentage) {
    }
}
