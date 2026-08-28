package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateInventoryAdjustmentCommand;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustment;

import java.util.UUID;

public interface ManageInventoryAdjustmentUseCase {

    InventoryAdjustment createAdjustment(CreateInventoryAdjustmentCommand command);

    /** Confirma el ajuste y postea un movimiento por línea (ENTITIES.md §18.2). */
    InventoryAdjustment approveAdjustment(UUID adjustmentId, UUID approvedBy);

    InventoryAdjustment getAdjustmentById(UUID adjustmentId);
}
