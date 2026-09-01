package io.github.KevinMitsi.inventories.application.port.in.command;

import java.time.LocalDate;
import java.util.UUID;

public record UpdatePriceListCommand(UUID priceListId, String name, String description,
                                     LocalDate validFrom, LocalDate validUntil) {
}
