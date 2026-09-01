package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

public record UpdateCarrierCommand(UUID carrierId, String name, String phone, String email) {
}
