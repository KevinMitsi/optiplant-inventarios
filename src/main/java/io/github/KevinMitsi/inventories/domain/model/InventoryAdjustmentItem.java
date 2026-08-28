package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Línea de un ajuste de inventario formal (ENTITIES.md §18.2).
 *
 * <p>A diferencia de {@link Quantity}, aquí el signo sí es significativo: positivo entra,
 * negativo sale. Es la única cantidad del dominio que se modela con signo — todas las demás
 * ({@code Inventory.quantity}, {@code InventoryMovement.quantity}) son siempre no negativas
 * y reciben su sentido de un tipo de movimiento explícito.
 */
public final class InventoryAdjustmentItem {

    private static final int REASON_MAX_LENGTH = 250;

    private final UUID id;
    private final UUID productId;
    private final BigDecimal quantityDelta;
    private final String reason;

    private InventoryAdjustmentItem(UUID id, UUID productId, BigDecimal quantityDelta, String reason) {
        this.id = Objects.requireNonNull(id, "El identificador de la línea no puede ser nulo.");
        this.productId = Objects.requireNonNull(productId, "La línea debe referenciar un producto.");
        this.quantityDelta = requireNonZero(quantityDelta);
        this.reason = normalizeReason(reason);
    }

    public static InventoryAdjustmentItem create(UUID productId, BigDecimal quantityDelta, String reason) {
        return new InventoryAdjustmentItem(UUID.randomUUID(), productId, quantityDelta, reason);
    }

    public static InventoryAdjustmentItem reconstitute(UUID id, UUID productId, BigDecimal quantityDelta, String reason) {
        return new InventoryAdjustmentItem(id, productId, quantityDelta, reason);
    }

    private static BigDecimal requireNonZero(BigDecimal delta) {
        Objects.requireNonNull(delta, "La cantidad de la línea de ajuste es obligatoria.");
        if (delta.signum() == 0) {
            throw new DomainValidationException("quantityDelta",
                    "La cantidad de una línea de ajuste no puede ser cero.");
        }
        return delta;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() > REASON_MAX_LENGTH) {
            throw new DomainValidationException("reason",
                    "El motivo de la línea no puede superar %d caracteres.".formatted(REASON_MAX_LENGTH));
        }
        return normalized;
    }

    public boolean isEntry() {
        return quantityDelta.signum() > 0;
    }

    /** Magnitud absoluta de la línea, ya lista para postear como {@link Quantity}. */
    public Quantity absoluteQuantity() {
        return Quantity.of(quantityDelta.abs());
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof InventoryAdjustmentItem item && id.equals(item.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
