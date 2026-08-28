package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Orden interna de posteo de movimiento, dirigida a {@link InventoryMovementPoster}.
 *
 * <p>No es un caso de uso ni viaja por HTTP: es el contrato entre los servicios de compras,
 * ventas, transferencias y ajustes y el único componente que tiene permitido tocar
 * {@code inventory.quantity}. Por eso vive en {@code application.service} y no en
 * {@code application.port.in} junto a los comandos que sí llegan desde un controlador.
 *
 * @param quantity        cantidad del movimiento, ya convertida a unidad base y siempre positiva
 * @param unitCost        costo unitario en unidad base; obligatorio solo para
 *                        {@link InventoryMovementType#PURCHASE_IN} (RF-23), nulo en cualquier otro caso
 * @param productSku      solo para componer un mensaje legible si el stock resulta insuficiente
 * @param purchaseOrderId como mucho una de estas cuatro referencias puede ser no nula
 */
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

    /** Movimiento sin documento de origen: entrada/salida manual o ajuste formal. */
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
