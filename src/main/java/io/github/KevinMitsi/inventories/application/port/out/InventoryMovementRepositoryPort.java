package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;

public interface InventoryMovementRepositoryPort {

    /** Nunca se actualiza ni se borra: es el histórico inmutable (RNF-12). */
    InventoryMovement save(InventoryMovement movement);

    PageResult<InventoryMovement> findByInventoryId(UUID inventoryId, PageQuery pageQuery);
}
