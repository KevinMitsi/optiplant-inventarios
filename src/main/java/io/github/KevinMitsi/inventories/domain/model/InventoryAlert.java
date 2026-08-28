package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Alerta de reabastecimiento sobre un saldo de inventario (RF-16, funcionalidad adicional
 * §34: "Sistema de alertas inteligentes de inventario").
 *
 * <p>La abre y la resuelve exclusivamente {@code InventoryMovementPoster}, comparando el
 * saldo resultante de cada movimiento contra el mínimo configurado — nunca un proceso
 * separado que tendría que releer el inventario y podría desincronizarse. El descarte manual
 * ({@link #dismiss()}) es la única transición que inicia un usuario.
 */
public final class InventoryAlert {

    private static final int MESSAGE_MAX_LENGTH = 500;

    private final UUID id;
    private final UUID inventoryId;
    private final InventoryAlertType alertType;
    private final Quantity triggeredQuantity;
    private final Quantity minimumStock;
    private final String message;
    private final Instant createdAt;

    private InventoryAlertStatus status;
    private Instant resolvedAt;

    private InventoryAlert(UUID id,
                           UUID inventoryId,
                           InventoryAlertType alertType,
                           InventoryAlertStatus status,
                           Quantity triggeredQuantity,
                           Quantity minimumStock,
                           String message,
                           Instant createdAt,
                           Instant resolvedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la alerta no puede ser nulo.");
        this.inventoryId = Objects.requireNonNull(inventoryId, "La alerta debe referenciar un inventario.");
        this.alertType = Objects.requireNonNull(alertType, "El tipo de alerta es obligatorio.");
        this.status = Objects.requireNonNull(status, "El estado de la alerta es obligatorio.");
        this.triggeredQuantity = Objects.requireNonNull(triggeredQuantity, "La cantidad disparadora es obligatoria.");
        this.minimumStock = Objects.requireNonNull(minimumStock, "El mínimo configurado es obligatorio.");
        this.message = truncate(message);
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria.");
        this.resolvedAt = resolvedAt;
    }

    public static InventoryAlert open(UUID inventoryId,
                                      InventoryAlertType alertType,
                                      Quantity triggeredQuantity,
                                      Quantity minimumStock) {
        return new InventoryAlert(UUID.randomUUID(), inventoryId, alertType, InventoryAlertStatus.OPEN,
                triggeredQuantity, minimumStock, defaultMessage(alertType, triggeredQuantity, minimumStock),
                Instant.now(), null);
    }

    public static InventoryAlert reconstitute(UUID id,
                                              UUID inventoryId,
                                              InventoryAlertType alertType,
                                              InventoryAlertStatus status,
                                              Quantity triggeredQuantity,
                                              Quantity minimumStock,
                                              String message,
                                              Instant createdAt,
                                              Instant resolvedAt) {
        return new InventoryAlert(id, inventoryId, alertType, status, triggeredQuantity, minimumStock,
                message, createdAt, resolvedAt);
    }

    /** El stock volvió a superar el mínimo: la condición que la disparó ya no existe. */
    public void resolve() {
        requireOpen("resolver");
        this.status = InventoryAlertStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }

    /** Un usuario la descarta explícitamente, aunque la condición de stock siga vigente. */
    public void dismiss() {
        requireOpen("descartar");
        this.status = InventoryAlertStatus.DISMISSED;
        this.resolvedAt = Instant.now();
    }

    private void requireOpen(String operation) {
        if (status != InventoryAlertStatus.OPEN) {
            throw new InvalidStateTransitionException("InventoryAlert", status, operation);
        }
    }

    private static String defaultMessage(InventoryAlertType type, Quantity triggered, Quantity minimum) {
        String base = type == InventoryAlertType.OUT_OF_STOCK
                ? "Producto agotado."
                : "Stock bajo: %s disponibles, mínimo configurado %s.".formatted(triggered, minimum);
        return truncate(base);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > MESSAGE_MAX_LENGTH ? trimmed.substring(0, MESSAGE_MAX_LENGTH) : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInventoryId() {
        return inventoryId;
    }

    public InventoryAlertType getAlertType() {
        return alertType;
    }

    public InventoryAlertStatus getStatus() {
        return status;
    }

    public Quantity getTriggeredQuantity() {
        return triggeredQuantity;
    }

    public Quantity getMinimumStock() {
        return minimumStock;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof InventoryAlert alert && id.equals(alert.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "InventoryAlert[type=%s, status=%s, inventoryId=%s]".formatted(alertType, status, inventoryId);
    }
}
