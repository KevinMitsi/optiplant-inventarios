package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Solicita una transferencia de mercancía entre dos sucursales (HU-27, RN-07). */
public record CreateTransferCommand(UUID originBranchId, UUID destinationBranchId, UUID requestedBy,
                                    String transferNumber, String priority, String notes, List<Item> items) {

    public record Item(UUID productId, BigDecimal quantity) {
    }
}
