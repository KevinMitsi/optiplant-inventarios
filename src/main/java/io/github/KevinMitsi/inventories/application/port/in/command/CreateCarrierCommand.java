package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

public record CreateCarrierCommand(UUID organizationId, String code, String name, String phone, String email) {
}
