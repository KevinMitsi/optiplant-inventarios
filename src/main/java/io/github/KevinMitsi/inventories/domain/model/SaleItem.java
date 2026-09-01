package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Objects;
import java.util.UUID;

/**
 * Línea de una venta (ENTITIES.md §11.3).
 *
 * <p>{@code unitPrice} es un snapshot del precio aplicado en el momento de la venta, tomado
 * de la lista de precios o indicado manualmente (HU-24, HU-25). Aunque el precio de catálogo
 * cambie después, esta línea histórica sigue mostrando lo que realmente se cobró (DBD-09).
 */
public final class SaleItem {

    private final UUID id;
    private final UUID productId;
    private final UUID productUnitId;
    private final Quantity quantity;
    private final Money unitPrice;
    private final Percentage discountPercentage;

    private SaleItem(UUID id, UUID productId, UUID productUnitId, Quantity quantity, Money unitPrice,
                     Percentage discountPercentage) {
        this.id = Objects.requireNonNull(id, "El identificador de la línea no puede ser nulo.");
        this.productId = Objects.requireNonNull(productId, "La línea debe referenciar un producto.");
        this.productUnitId = Objects.requireNonNull(productUnitId, "La línea debe referenciar una presentación.");
        this.quantity = requirePositive(quantity);
        this.unitPrice = Objects.requireNonNull(unitPrice, "El precio unitario es obligatorio.");
        this.discountPercentage = discountPercentage == null ? Percentage.ZERO : discountPercentage;
    }

    public static SaleItem create(UUID productId, UUID productUnitId, Quantity quantity, Money unitPrice,
                                  Percentage discountPercentage) {
        return new SaleItem(UUID.randomUUID(), productId, productUnitId, quantity, unitPrice, discountPercentage);
    }

    public static SaleItem reconstitute(UUID id, UUID productId, UUID productUnitId, Quantity quantity,
                                        Money unitPrice, Percentage discountPercentage) {
        return new SaleItem(id, productId, productUnitId, quantity, unitPrice, discountPercentage);
    }

    /** Precio neto por unidad, ya con el descuento de la línea aplicado (HU-24). */
    public Money netUnitPrice() {
        return unitPrice.applyDiscount(discountPercentage);
    }

    /** Subtotal de la línea: precio neto por cantidad. */
    public Money subtotal() {
        return netUnitPrice().multiply(quantity);
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
        return other instanceof SaleItem item && id.equals(item.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
