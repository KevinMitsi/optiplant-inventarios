package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/** Instruccion de alta de una categoria de productos. */
public record CreateCategoryCommand(UUID organizationId,
                                    String code,
                                    String name,
                                    String description) {
}
