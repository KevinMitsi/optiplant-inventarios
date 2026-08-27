package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Importe monetario.
 *
 * <p>{@code BigDecimal} con escala fija de 4 decimales, igual que {@code DECIMAL(18,4)}
 * en la base. Nunca punto flotante (DBD-06): un céntimo perdido por redondeo en cada
 * línea de venta acaba descuadrando la valoración del inventario.
 *
 * <p>Cuatro decimales y no dos porque el costo promedio ponderado es un cociente y
 * necesita precisión intermedia; redondear a dos en cada compra desviaría el costo
 * acumulado del producto (RF-23).
 *
 * <p>Todos los redondeos usan {@link RoundingMode#HALF_UP}, el criterio comercial
 * habitual, aplicado de forma uniforme para que el mismo cálculo dé siempre lo mismo.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    /** Decimales de un importe, alineados con la columna {@code DECIMAL(18,4)}. */
    public static final int SCALE = 4;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Precisión de trabajo para divisiones, holgada frente a la escala final. */
    private static final MathContext DIVISION_CONTEXT = new MathContext(20, ROUNDING);

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "El importe no puede ser nulo.");
        if (amount.signum() < 0) {
            throw new DomainValidationException(
                    "El importe no puede ser negativo: %s.".formatted(amount.toPlainString()));
        }
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new DomainValidationException(
                    "La resta dejaría un importe negativo: %s - %s."
                            .formatted(amount.toPlainString(), other.amount.toPlainString()));
        }
        return new Money(result);
    }

    /** Importe unitario por cantidad. Es el cálculo del subtotal de una línea. */
    public Money multiply(Quantity quantity) {
        return new Money(amount.multiply(quantity.value()));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    /**
     * Reparte el importe entre una cantidad. Es el cálculo del costo unitario.
     *
     * <p>Divide con precisión ampliada y redondea una sola vez al final, para no arrastrar
     * el error de un redondeo intermedio.
     *
     * @throws DomainValidationException si la cantidad es cero
     */
    public Money divide(Quantity quantity) {
        if (quantity.isZero()) {
            throw new DomainValidationException("No se puede dividir un importe entre una cantidad cero.");
        }
        return new Money(amount.divide(quantity.value(), DIVISION_CONTEXT));
    }

    /** Descuenta un porcentaje y devuelve el importe neto resultante. */
    public Money applyDiscount(Percentage discount) {
        return new Money(amount.multiply(discount.asRemainingFactor()));
    }

    /** Parte del importe que representa ese porcentaje. */
    public Money percentageOf(Percentage percentage) {
        return new Money(amount.multiply(percentage.asFactor()));
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isGreaterThan(Money other) {
        return amount.compareTo(other.amount) > 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
