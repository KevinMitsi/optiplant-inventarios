package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Porcentaje entre 0 y 100, con dos decimales.
 *
 * <p>Modela los descuentos de compras y ventas (RF-20, RF-28). Existe como tipo propio
 * para dos cosas: encerrar el rango válido en un único sitio, en lugar de repetir la
 * comprobación {@code 0..100} en cada servicio, y despejar la ambigüedad de si un 15 %
 * se guarda como {@code 15} o como {@code 0.15}. Aquí siempre es {@code 15}, y la
 * conversión a factor se pide de forma explícita.
 */
public record Percentage(BigDecimal value) implements Comparable<Percentage> {

    /** Decimales de un porcentaje, alineados con la columna {@code DECIMAL(5,2)}. */
    public static final int SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static final Percentage ZERO = new Percentage(BigDecimal.ZERO);

    public Percentage {
        Objects.requireNonNull(value, "El porcentaje no puede ser nulo.");
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new DomainValidationException(
                    "El porcentaje debe estar entre 0 y 100: %s.".formatted(value.toPlainString()));
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }

    public static Percentage of(String value) {
        return new Percentage(new BigDecimal(value));
    }

    /** Devuelve {@code ZERO} ante un nulo, para campos de descuento opcionales. */
    public static Percentage ofNullable(BigDecimal value) {
        return value == null ? ZERO : new Percentage(value);
    }

    /** Fracción que representa. Un 15 % devuelve {@code 0.15}. */
    public BigDecimal asFactor() {
        return value.divide(HUNDRED, SCALE + 4, RoundingMode.HALF_UP);
    }

    /** Fracción que queda tras aplicarlo. Un 15 % de descuento devuelve {@code 0.85}. */
    public BigDecimal asRemainingFactor() {
        return BigDecimal.ONE.subtract(asFactor());
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    @Override
    public int compareTo(Percentage other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString() + "%";
    }
}
