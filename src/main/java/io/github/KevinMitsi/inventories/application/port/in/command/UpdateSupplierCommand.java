package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

public record UpdateSupplierCommand(UUID supplierId, String name, String taxId, String email, String phone) {
}
