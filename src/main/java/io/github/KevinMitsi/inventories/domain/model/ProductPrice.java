package io.github.KevinMitsi.inventories.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Precio de un producto dentro de una lista de precios (ENTITIES.md §12.2).
 *
 * <p>Un precio por producto, no por presentación: cada variante es un producto propio y por
 * tanto lleva su propio precio, sin factores que traducir.
 *
 * <p>Entidad independiente y no una colección anidada de {@link PriceList}: una lista puede
 * cubrir todo el catálogo, y el caso de uso habitual es "el precio de este producto en esta
 * lista", una consulta puntual, no "todos los precios de esta lista".
 */
public final class ProductPrice {

    private final UUID id;
    private final UUID priceListId;
    private final UUID productId;

    private Money price;

    private ProductPrice(UUID id, UUID priceListId, UUID productId, Money price) {
        this.id = Objects.requireNonNull(id, "El identificador del precio no puede ser nulo.");
        this.priceListId = Objects.requireNonNull(priceListId, "El precio debe pertenecer a una lista.");
        this.productId = Objects.requireNonNull(productId, "El precio debe referenciar un producto.");
        this.price = Objects.requireNonNull(price, "El precio es obligatorio.");
    }

    public static ProductPrice create(UUID priceListId, UUID productId, Money price) {
        return new ProductPrice(UUID.randomUUID(), priceListId, productId, price);
    }

    public static ProductPrice reconstitute(UUID id, UUID priceListId, UUID productId,
                                            Money price) {
        return new ProductPrice(id, priceListId, productId, price);
    }

    public void changePrice(Money newPrice) {
        this.price = Objects.requireNonNull(newPrice, "El precio es obligatorio.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getPriceListId() {
        return priceListId;
    }

    public UUID getProductId() {
        return productId;
    }

    public Money getPrice() {
        return price;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ProductPrice that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
