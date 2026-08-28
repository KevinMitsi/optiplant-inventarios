package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Orden interna de posteo de movimiento, dirigida a {@link InventoryMovementPoster}. */
public record PostInventoryMovementCommand(UUID branchId,
                                           UUID productId,
                                           String productSku,
                                           InventoryMovementType movementType,
                                           BigDecimal quantity,
                                           BigDecimal unitCost,
                                           String reason,
                                           UUID userId,
                                           Instant occurredAt,
                                           UUID purchaseOrderId,
                                           UUID saleId,
                                           UUID transferId,
                                           UUID adjustmentId) {

    public static PostInventoryMovementCommand withoutReference(UUID branchId,
                                                                 UUID productId,
                                                                 String productSku,
                                                                 InventoryMovementType movementType,
                                                                 BigDecimal quantity,
                                                                 String reason,
                                                                 UUID userId) {
        return new PostInventoryMovementCommand(branchId, productId, productSku, movementType, quantity,
                null, reason, userId, Instant.now(), null, null, null, null);
    }

    public static PostInventoryMovementCommand forAdjustment(UUID branchId,
                                                              UUID productId,
                                                              String productSku,
                                                              InventoryMovementType movementType,
                                                              BigDecimal quantity,
                                                              String reason,
                                                              UUID userId,
                                                              UUID adjustmentId) {
        return new PostInventoryMovementCommand(branchId, productId, productSku, movementType, quantity,
                null, reason, userId, Instant.now(), null, null, null, adjustmentId);
    }
}
