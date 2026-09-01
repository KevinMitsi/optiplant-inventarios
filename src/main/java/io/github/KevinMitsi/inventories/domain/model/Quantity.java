package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Cantidad de inventario.
 *
 * <p>Siempre {@code BigDecimal} y nunca {@code double}: hay productos que se miden en kg,
 * litros o metros, y en punto flotante {@code 0.1 + 0.2} no da {@code 0.3}. En un sistema
 * cuyo invariante central es que el saldo cuadre con sus movimientos, ese error acumulado
 * sería indetectable y corrompería el histórico (DBD-05).
 *
 * <p>Fija la escala en 6 decimales, la misma que {@code DECIMAL(18,6)} en la base. Al
 * normalizarla en el constructor, dos cantidades que representan lo mismo son iguales
 * también para {@code equals}, cosa que {@code BigDecimal} por sí solo no garantiza:
 * {@code new BigDecimal("1.0").equals(new BigDecimal("1.00"))} es {@code false}.
 *
 * <p>Es inmutable: cada operación devuelve una instancia nueva.
 */
public record Quantity(BigDecimal value) implements Comparable<Quantity> {

    /** Decimales de una cantidad, alineados con la columna {@code DECIMAL(18,6)}. */
    public static final int SCALE = 6;

    public static final Quantity ZERO = new Quantity(BigDecimal.ZERO);

    public Quantity {
        Objects.requireNonNull(value, "La cantidad no puede ser nula.");
        if (value.signum() < 0) {
            throw new DomainValidationException(
                    "La cantidad no puede ser negativa: %s.".formatted(value.toPlainString()));
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }

    public static Quantity of(String value) {
        return new Quantity(new BigDecimal(value));
    }

    public static Quantity of(long value) {
        return new Quantity(BigDecimal.valueOf(value));
    }

    public Quantity add(Quantity other) {
        return new Quantity(value.add(other.value));
    }

    /**
     * Resta otra cantidad.
     *
     * @throws DomainValidationException si el resultado sería negativo. Que restar de más
     *         falle aquí, y no más tarde contra el {@code CHECK (quantity >= 0)} de la
     *         base, es lo que permite responder al usuario por qué no se pudo.
     */
    public Quantity subtract(Quantity other) {
        BigDecimal result = value.subtract(other.value);
        if (result.signum() < 0) {
            throw new DomainValidationException(
                    "La resta dejaría una cantidad negativa: %s - %s."
                            .formatted(value.toPlainString(), other.value.toPlainString()));
        }
        return new Quantity(result);
    }

    public Quantity multiply(BigDecimal factor) {
        return new Quantity(value.multiply(factor));
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public boolean isGreaterThan(Quantity other) {
        return value.compareTo(other.value) > 0;
    }

    public boolean isLessThan(Quantity other) {
        return value.compareTo(other.value) < 0;
    }

    public boolean isGreaterThanOrEqual(Quantity other) {
        return value.compareTo(other.value) >= 0;
    }

    public boolean isLessThanOrEqual(Quantity other) {
        return value.compareTo(other.value) <= 0;
    }

    @Override
    public int compareTo(Quantity other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
