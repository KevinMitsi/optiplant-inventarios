package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustment;

import java.util.Optional;
import java.util.UUID;

public interface InventoryAdjustmentRepositoryPort {

    InventoryAdjustment save(InventoryAdjustment adjustment);

    /** Carga el ajuste con sus líneas: el agregado nunca se devuelve incompleto. */
    Optional<InventoryAdjustment> findById(UUID id);
}
