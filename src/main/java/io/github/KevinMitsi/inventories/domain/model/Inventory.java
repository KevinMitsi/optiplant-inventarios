package io.github.KevinMitsi.inventories.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Saldo actual de un producto dentro de una sucursal (RN-02).
 *
 * <p>Es una proyección persistida del stock, distinta del histórico auditable que lleva
 * {@link InventoryMovement} (DBD-02, ENTITIES.md §8.2): consultar todo el histórico para
 * conocer el saldo sería innecesariamente costoso. La integridad entre ambos la garantiza
 * quien orquesta la transacción — {@code InventoryMovementPoster} — nunca este agregado por
 * sí solo, porque {@code Inventory} no puede, por construcción, insertar la fila de
 * movimiento que lo explique.
 *
 * <p>{@code version} respalda el bloqueo optimista de la fila (RNF-05): dos operaciones que
 * descuentan del mismo saldo a la vez no pueden confirmarse ambas sin arriesgar un stock
 * incoherente.
 */
public final class Inventory {

    private final UUID id;
    private final UUID branchId;
    private final UUID productId;

    private Quantity quantity;
    private Quantity minimumStock;
    private Money averageCost;
    private Instant updatedAt;
    private int version;

    private Inventory(UUID id,
                      UUID branchId,
                      UUID productId,
                      Quantity quantity,
                      Quantity minimumStock,
                      Money averageCost,
                      Instant updatedAt,
                      int version) {
        this.id = Objects.requireNonNull(id, "El identificador del inventario no puede ser nulo.");
        this.branchId = Objects.requireNonNull(branchId, "El inventario debe pertenecer a una sucursal.");
        this.productId = Objects.requireNonNull(productId, "El inventario debe referenciar un producto.");
        this.quantity = Objects.requireNonNull(quantity, "La cantidad no puede ser nula.");
        this.minimumStock = Objects.requireNonNull(minimumStock, "El stock mínimo no puede ser nulo.");
        this.averageCost = Objects.requireNonNull(averageCost, "El costo promedio no puede ser nulo.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
        this.version = version;
    }

    /** Abre el saldo de un producto en una sucursal, en cero, la primera vez que se mueve. */
    public static Inventory open(UUID branchId, UUID productId) {
        return new Inventory(UUID.randomUUID(), branchId, productId,
                Quantity.ZERO, Quantity.ZERO, Money.ZERO, Instant.now(), 0);
    }

    public static Inventory reconstitute(UUID id,
                                         UUID branchId,
                                         UUID productId,
                                         Quantity quantity,
                                         Quantity minimumStock,
                                         Money averageCost,
                                         Instant updatedAt,
                                         int version) {
        return new Inventory(id, branchId, productId, quantity, minimumStock, averageCost, updatedAt, version);
    }

    /** Aplica una entrada que no altera el costo: transferencias, devoluciones, ajustes. */
    public void increase(Quantity delta) {
        this.quantity = quantity.add(delta);
        touch();
    }

    /**
     * Aplica una salida de stock.
     *
     * <p>Deliberadamente no lanza el error de negocio "stock insuficiente": quien orquesta
     * la operación conoce el SKU y la sucursal, y puede construir un
     * {@code InsufficientStockException} legible; aquí solo se defiende el invariante de no
     * negatividad del propio valor {@link Quantity}.
     */
    public void decrease(Quantity delta) {
        this.quantity = quantity.subtract(delta);
        touch();
    }

    /**
     * Aplica una entrada por compra y recalcula el costo promedio ponderado (RF-23, HU-21).
     *
     * <pre>
     * nuevoCosto = (saldoActual * costoActual + cantidadRecibida * costoRecibido)
     *              / (saldoActual + cantidadRecibida)
     * </pre>
     *
     * <p>Si el saldo previo era cero, el nuevo costo es directamente el de la compra: no hay
     * saldo anterior con el que ponderar.
     */
    public void receivePurchase(Quantity receivedQuantity, Money unitCost) {
        if (!quantity.isZero()) {
            Money currentValue = averageCost.multiply(quantity);
            Money incomingValue = unitCost.multiply(receivedQuantity);
            Quantity newQuantity = quantity.add(receivedQuantity);
            this.averageCost = currentValue.add(incomingValue).divide(newQuantity);
        } else {
            this.averageCost = unitCost;
        }
        this.quantity = quantity.add(receivedQuantity);
        touch();
    }

    public void setMinimumStock(Quantity newMinimum) {
        this.minimumStock = Objects.requireNonNull(newMinimum, "El stock mínimo no puede ser nulo.");
        touch();
    }

    /** Sin stock en absoluto, independientemente de si hay mínimo configurado (HU-40). */
    public boolean isOutOfStock() {
        return quantity.isZero();
    }

    /** Por debajo o igual al mínimo configurado. Sin mínimo definido, nunca dispara (RF-16). */
    public boolean isLowStock() {
        return minimumStock.isPositive() && quantity.isLessThanOrEqual(minimumStock);
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getProductId() {
        return productId;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public Quantity getMinimumStock() {
        return minimumStock;
    }

    public Money getAverageCost() {
        return averageCost;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Inventory inventory && id.equals(inventory.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Inventory[branchId=%s, productId=%s, quantity=%s, minimumStock=%s]"
                .formatted(branchId, productId, quantity, minimumStock);
    }
}
