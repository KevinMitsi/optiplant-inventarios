package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro inmutable de un cambio de stock (RN-04, RN-11).
 *
 * <p>Es el núcleo de integridad del dominio: <b>ningún saldo de {@link Inventory} cambia sin
 * que exista una fila aquí que lo explique</b>. Por eso no ofrece ningún método de
 * modificación después de creado — un error se corrige con un movimiento de ajuste nuevo,
 * nunca alterando uno existente (RNF-12).
 *
 * <p>La cantidad siempre es positiva; el sentido del cambio lo aporta
 * {@link InventoryMovementType#direction()}, nunca el signo (ENTITIES.md §8.4). El
 * documento de origen —si lo hay— se referencia con, como mucho, una de las cuatro claves
 * foráneas específicas, nunca más de una (ENTITIES.md §8.5).
 */
public final class InventoryMovement {

    private final UUID id;
    private final UUID inventoryId;
    private final InventoryMovementType movementType;
    private final UUID userId;
    private final Quantity quantity;
    private final Money unitCost;
    private final String reason;
    private final UUID purchaseOrderId;
    private final UUID saleId;
    private final UUID transferId;
    private final UUID adjustmentId;
    private final Instant occurredAt;
    private final Instant createdAt;

    private InventoryMovement(UUID id,
                              UUID inventoryId,
                              InventoryMovementType movementType,
                              UUID userId,
                              Quantity quantity,
                              Money unitCost,
                              String reason,
                              UUID purchaseOrderId,
                              UUID saleId,
                              UUID transferId,
                              UUID adjustmentId,
                              Instant occurredAt,
                              Instant createdAt) {
        this.id = Objects.requireNonNull(id, "El identificador del movimiento no puede ser nulo.");
        this.inventoryId = Objects.requireNonNull(inventoryId, "El movimiento debe referenciar un inventario.");
        this.movementType = Objects.requireNonNull(movementType, "El tipo de movimiento es obligatorio.");
        this.userId = Objects.requireNonNull(userId, "El movimiento debe registrar un responsable (RN-11).");
        this.quantity = requirePositive(quantity);
        this.unitCost = unitCost;
        this.reason = requireReason(reason);
        this.purchaseOrderId = purchaseOrderId;
        this.saleId = saleId;
        this.transferId = transferId;
        this.adjustmentId = adjustmentId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "La fecha del movimiento es obligatoria.");
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de registro es obligatoria.");
        requireAtMostOneReference();
    }

    public static InventoryMovement create(UUID inventoryId,
                                            InventoryMovementType movementType,
                                            UUID userId,
                                            Quantity quantity,
                                            Money unitCost,
                                            String reason,
                                            UUID purchaseOrderId,
                                            UUID saleId,
                                            UUID transferId,
                                            UUID adjustmentId,
                                            Instant occurredAt) {
        return new InventoryMovement(UUID.randomUUID(), inventoryId, movementType, userId, quantity, unitCost,
                reason, purchaseOrderId, saleId, transferId, adjustmentId, occurredAt, Instant.now());
    }

    public static InventoryMovement reconstitute(UUID id,
                                                  UUID inventoryId,
                                                  InventoryMovementType movementType,
                                                  UUID userId,
                                                  Quantity quantity,
                                                  Money unitCost,
                                                  String reason,
                                                  UUID purchaseOrderId,
                                                  UUID saleId,
                                                  UUID transferId,
                                                  UUID adjustmentId,
                                                  Instant occurredAt,
                                                  Instant createdAt) {
        return new InventoryMovement(id, inventoryId, movementType, userId, quantity, unitCost, reason,
                purchaseOrderId, saleId, transferId, adjustmentId, occurredAt, createdAt);
    }

    private static Quantity requirePositive(Quantity quantity) {
        Objects.requireNonNull(quantity, "La cantidad del movimiento es obligatoria.");
        if (!quantity.isPositive()) {
            throw new DomainValidationException("quantity",
                    "La cantidad de un movimiento debe ser mayor que cero.");
        }
        return quantity;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainValidationException("reason", "El motivo del movimiento es obligatorio (RN-11).");
        }
        String normalized = reason.trim();
        if (normalized.length() > 250) {
            throw new DomainValidationException("reason", "El motivo no puede superar 250 caracteres.");
        }
        return normalized;
    }

    private void requireAtMostOneReference() {
        int references = 0;
        if (purchaseOrderId != null) {
            references++;
        }
        if (saleId != null) {
            references++;
        }
        if (transferId != null) {
            references++;
        }
        if (adjustmentId != null) {
            references++;
        }
        if (references > 1) {
            throw new DomainValidationException("reference",
                    "Un movimiento solo puede referenciar, como mucho, un documento de origen.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getInventoryId() {
        return inventoryId;
    }

    public InventoryMovementType getMovementType() {
        return movementType;
    }

    public UUID getUserId() {
        return userId;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public Money getUnitCost() {
        return unitCost;
    }

    public String getReason() {
        return reason;
    }

    public UUID getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public UUID getSaleId() {
        return saleId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getAdjustmentId() {
        return adjustmentId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof InventoryMovement movement && id.equals(movement.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "InventoryMovement[type=%s, quantity=%s, inventoryId=%s]"
                .formatted(movementType, quantity, inventoryId);
    }
}
