package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Objects;
import java.util.UUID;

/**
 * Línea de una transferencia (ENTITIES.md §13.5).
 *
 * <p>Encadena cuatro cantidades que solo pueden reducirse en cada paso —
 * {@code requested ≥ approved ≥ shipped ≥ received} — reflejando que la transferencia va
 * concretándose: se pide una cantidad, se aprueba (posiblemente menos), se despacha lo
 * aprobado (o menos, si no cabía todo) y se recibe lo que realmente llegó. La cantidad
 * faltante no se persiste (ENTITIES.md §13.6): se deriva con {@link #missingQuantity()}.
 */
public final class TransferItem {

    private final UUID id;
    private final UUID productId;
    private final UUID productUnitId;
    private final Quantity requestedQuantity;

    private Quantity approvedQuantity;
    private Quantity shippedQuantity;
    private Quantity receivedQuantity;

    private TransferItem(UUID id, UUID productId, UUID productUnitId, Quantity requestedQuantity,
                         Quantity approvedQuantity, Quantity shippedQuantity, Quantity receivedQuantity) {
        this.id = Objects.requireNonNull(id, "El identificador de la línea no puede ser nulo.");
        this.productId = Objects.requireNonNull(productId, "La línea debe referenciar un producto.");
        this.productUnitId = Objects.requireNonNull(productUnitId, "La línea debe referenciar una presentación.");
        this.requestedQuantity = requirePositive(requestedQuantity);
        this.approvedQuantity = approvedQuantity;
        this.shippedQuantity = shippedQuantity;
        this.receivedQuantity = receivedQuantity;
    }

    public static TransferItem create(UUID productId, UUID productUnitId, Quantity requestedQuantity) {
        return new TransferItem(UUID.randomUUID(), productId, productUnitId, requestedQuantity, null, null, null);
    }

    public static TransferItem reconstitute(UUID id, UUID productId, UUID productUnitId, Quantity requestedQuantity,
                                            Quantity approvedQuantity, Quantity shippedQuantity,
                                            Quantity receivedQuantity) {
        return new TransferItem(id, productId, productUnitId, requestedQuantity, approvedQuantity, shippedQuantity,
                receivedQuantity);
    }

    /** Fija la cantidad aprobada (HU-29): puede ser menor que la pedida, nunca mayor. */
    void approve(Quantity quantity) {
        Objects.requireNonNull(quantity, "La cantidad aprobada es obligatoria.");
        if (quantity.isGreaterThan(requestedQuantity)) {
            throw new DomainValidationException("approvedQuantity",
                    "La cantidad aprobada (%s) no puede superar la solicitada (%s).".formatted(quantity, requestedQuantity));
        }
        this.approvedQuantity = quantity;
    }

    /** Fija la cantidad despachada (RN-08 la valida contra el stock, no esta línea). */
    void ship(Quantity quantity) {
        Objects.requireNonNull(quantity, "La cantidad despachada es obligatoria.");
        if (approvedQuantity == null) {
            throw new DomainValidationException("shippedQuantity",
                    "No se puede despachar una línea que no fue aprobada.");
        }
        if (quantity.isGreaterThan(approvedQuantity)) {
            throw new DomainValidationException("shippedQuantity",
                    "La cantidad despachada (%s) no puede superar la aprobada (%s).".formatted(quantity, approvedQuantity));
        }
        this.shippedQuantity = quantity;
    }

    /** Fija la cantidad realmente recibida (RN-09): lo que llegó, ni más ni menos. */
    void receive(Quantity quantity) {
        Objects.requireNonNull(quantity, "La cantidad recibida es obligatoria.");
        if (shippedQuantity == null) {
            throw new DomainValidationException("receivedQuantity",
                    "No se puede recibir una línea que no fue despachada.");
        }
        if (quantity.isGreaterThan(shippedQuantity)) {
            throw new DomainValidationException("receivedQuantity",
                    "La cantidad recibida (%s) no puede superar la despachada (%s).".formatted(quantity, shippedQuantity));
        }
        this.receivedQuantity = quantity;
    }

    /** Cantidad que se despachó pero no llegó (RN-10). Cero si aún no hay recepción. */
    public Quantity missingQuantity() {
        if (shippedQuantity == null || receivedQuantity == null) {
            return Quantity.ZERO;
        }
        return shippedQuantity.subtract(receivedQuantity);
    }

    public boolean isFullyReceived() {
        return receivedQuantity != null && shippedQuantity != null
                && receivedQuantity.isGreaterThanOrEqual(shippedQuantity);
    }

    private static Quantity requirePositive(Quantity quantity) {
        Objects.requireNonNull(quantity, "La cantidad solicitada de la línea es obligatoria.");
        if (!quantity.isPositive()) {
            throw new DomainValidationException("requestedQuantity",
                    "La cantidad solicitada de la línea debe ser mayor que cero.");
        }
        return quantity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getProductUnitId() {
        return productUnitId;
    }

    public Quantity getRequestedQuantity() {
        return requestedQuantity;
    }

    public Quantity getApprovedQuantity() {
        return approvedQuantity;
    }

    public Quantity getShippedQuantity() {
        return shippedQuantity;
    }

    public Quantity getReceivedQuantity() {
        return receivedQuantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TransferItem item && id.equals(item.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
