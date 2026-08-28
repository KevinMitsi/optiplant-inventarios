package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Objects;
import java.util.UUID;

/**
 * Línea de una orden de compra (ENTITIES.md §10.4).
 *
 * <p>{@code unitPrice} y {@code discountPercentage} son snapshots de la condición pactada en
 * <em>esta</em> compra, no el precio vigente del catálogo (DBD-09): si el precio del producto
 * cambia después, esta línea histórica sigue mostrando lo que realmente se pagó.
 */
public final class PurchaseOrderItem {

    private final UUID id;
    private final UUID productId;
    private final UUID productUnitId;
    private final Quantity quantity;
    private final Money unitPrice;
    private final Percentage discountPercentage;

    private Quantity receivedQuantity;

    private PurchaseOrderItem(UUID id, UUID productId, UUID productUnitId, Quantity quantity,
                              Quantity receivedQuantity, Money unitPrice, Percentage discountPercentage) {
        this.id = Objects.requireNonNull(id, "El identificador de la línea no puede ser nulo.");
        this.productId = Objects.requireNonNull(productId, "La línea debe referenciar un producto.");
        this.productUnitId = Objects.requireNonNull(productUnitId, "La línea debe referenciar una presentación.");
        this.quantity = requirePositive(quantity);
        this.receivedQuantity = Objects.requireNonNull(receivedQuantity, "La cantidad recibida no puede ser nula.");
        this.unitPrice = Objects.requireNonNull(unitPrice, "El precio unitario es obligatorio.");
        this.discountPercentage = discountPercentage == null ? Percentage.ZERO : discountPercentage;
        if (this.receivedQuantity.isGreaterThan(this.quantity)) {
            throw new DomainValidationException("receivedQuantity",
                    "La cantidad recibida no puede superar la solicitada.");
        }
    }

    public static PurchaseOrderItem create(UUID productId, UUID productUnitId, Quantity quantity,
                                           Money unitPrice, Percentage discountPercentage) {
        return new PurchaseOrderItem(UUID.randomUUID(), productId, productUnitId, quantity, Quantity.ZERO,
                unitPrice, discountPercentage);
    }

    public static PurchaseOrderItem reconstitute(UUID id, UUID productId, UUID productUnitId, Quantity quantity,
                                                 Quantity receivedQuantity, Money unitPrice,
                                                 Percentage discountPercentage) {
        return new PurchaseOrderItem(id, productId, productUnitId, quantity, receivedQuantity, unitPrice,
                discountPercentage);
    }

    /**
     * Registra la recepción de mercancía sobre esta línea.
     *
     * @return la cantidad efectivamente aceptada, que puede ser menor que la pedida si es la
     *         última recepción posible sobre la línea
     * @throws BusinessRuleViolationException si la línea ya está completamente recibida
     */
    public void receive(Quantity quantityReceivedNow) {
        if (isFullyReceived()) {
            throw new BusinessRuleViolationException("RF-21",
                    "Esta línea ya fue recibida por completo.");
        }
        Quantity total = receivedQuantity.add(quantityReceivedNow);
        if (total.isGreaterThan(quantity)) {
            throw new BusinessRuleViolationException("RF-21",
                    "La recepción dejaría la cantidad recibida (%s) por encima de la solicitada (%s)."
                            .formatted(total, quantity));
        }
        this.receivedQuantity = total;
    }

    public boolean isFullyReceived() {
        return receivedQuantity.isGreaterThanOrEqual(quantity);
    }

    public Quantity pendingQuantity() {
        return quantity.subtract(receivedQuantity);
    }

    /** Precio neto por unidad, ya con el descuento de la línea aplicado. */
    public Money netUnitPrice() {
        return unitPrice.applyDiscount(discountPercentage);
    }

    private static Quantity requirePositive(Quantity quantity) {
        Objects.requireNonNull(quantity, "La cantidad de la línea es obligatoria.");
        if (!quantity.isPositive()) {
            throw new DomainValidationException("quantity", "La cantidad de la línea debe ser mayor que cero.");
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

    public Quantity getQuantity() {
        return quantity;
    }

    public Quantity getReceivedQuantity() {
        return receivedQuantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public Percentage getDiscountPercentage() {
        return discountPercentage;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PurchaseOrderItem item && id.equals(item.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
