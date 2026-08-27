package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/** El codigo forma parte de la identidad de la categoria y no es modificable. */
public record UpdateCategoryCommand(UUID categoryId, String name, String description) {
}
