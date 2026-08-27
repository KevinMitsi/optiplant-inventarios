package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Presentación en la que se compra, almacena o vende un producto (RF-09).
 *
 * <p>El factor de conversión expresa cuántas unidades base equivale una de esta
 * presentación: si la base es la botella, una caja de 24 tiene factor {@code 24}. La unidad
 * base tiene factor {@code 1} por definición, y ese es su distintivo real.
 *
 * <p>Es una entidad hija de {@link Product}: no existe por sí sola y se manipula siempre a
 * través del agregado, que es quien puede garantizar que haya exactamente una base activa.
 */
public final class ProductUnit {

    private final UUID id;
    private final UnitOfMeasure unit;

    private BigDecimal conversionFactor;
    private boolean baseUnit;
    private boolean active;

    private ProductUnit(UUID id,
                        UnitOfMeasure unit,
                        BigDecimal conversionFactor,
                        boolean baseUnit,
                        boolean active) {
        this.id = Objects.requireNonNull(id, "El identificador de la presentación no puede ser nulo.");
        this.unit = Objects.requireNonNull(unit, "La presentación debe referenciar una unidad de medida.");
        this.conversionFactor = requireFactor(conversionFactor, baseUnit);
        this.baseUnit = baseUnit;
        this.active = active;
    }

    public static ProductUnit createBase(UnitOfMeasure unit) {
        return new ProductUnit(UUID.randomUUID(), unit, BigDecimal.ONE, true, true);
    }

    public static ProductUnit create(UnitOfMeasure unit, BigDecimal conversionFactor) {
        return new ProductUnit(UUID.randomUUID(), unit, conversionFactor, false, true);
    }

    public static ProductUnit reconstitute(UUID id,
                                           UnitOfMeasure unit,
                                           BigDecimal conversionFactor,
                                           boolean baseUnit,
                                           boolean active) {
        return new ProductUnit(id, unit, conversionFactor, baseUnit, active);
    }

    /** Convierte una cantidad expresada en esta presentación a unidades base. */
    public Quantity toBaseQuantity(Quantity quantity) {
        return quantity.multiply(conversionFactor);
    }

    void promoteToBase() {
        this.baseUnit = true;
        this.conversionFactor = BigDecimal.ONE;
    }

    void demoteFromBase(BigDecimal newFactor) {
        this.conversionFactor = requireFactor(newFactor, false);
        this.baseUnit = false;
    }

    void changeFactor(BigDecimal newFactor) {
        if (baseUnit) {
            throw new DomainValidationException("conversionFactor",
                    "La unidad base siempre tiene factor 1; para cambiarlo, designe otra presentación como base.");
        }
        this.conversionFactor = requireFactor(newFactor, false);
    }

    void deactivate() {
        this.active = false;
    }

    void activate() {
        this.active = true;
    }

    private static BigDecimal requireFactor(BigDecimal factor, boolean isBaseUnit) {
        if (factor == null) {
            throw new DomainValidationException("conversionFactor",
                    "El factor de conversión es obligatorio.");
        }
        if (factor.signum() <= 0) {
            throw new DomainValidationException("conversionFactor",
                    "El factor de conversión debe ser mayor que cero.");
        }
        if (isBaseUnit && factor.compareTo(BigDecimal.ONE) != 0) {
            throw new DomainValidationException("conversionFactor",
                    "La unidad base debe tener factor de conversión 1.");
        }
        return factor;
    }

    public UUID getId() {
        return id;
    }

    public UnitOfMeasure getUnit() {
        return unit;
    }

    public UUID getUnitId() {
        return unit.id();
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public boolean isBaseUnit() {
        return baseUnit;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ProductUnit productUnit && id.equals(productUnit.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ProductUnit[unit=%s, factor=%s, base=%s]"
                .formatted(unit.code(), conversionFactor.toPlainString(), baseUnit);
    }
}
