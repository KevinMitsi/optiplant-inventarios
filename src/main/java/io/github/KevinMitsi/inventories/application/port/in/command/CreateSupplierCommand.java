package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

public record CreateSupplierCommand(UUID organizationId, String code, String name, String taxId,
                                    String email, String phone) {
}
