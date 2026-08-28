package io.github.KevinMitsi.inventories.application.port.in.command;

import java.time.LocalDate;
import java.util.UUID;

public record CreatePriceListCommand(UUID organizationId, String code, String name, String description,
                                     LocalDate validFrom, LocalDate validUntil) {
}
