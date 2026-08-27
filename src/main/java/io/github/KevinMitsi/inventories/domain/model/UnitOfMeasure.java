package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Unidad de medida del catálogo global: unidad, kilogramo, litro, caja...
 *
 * <p>Es tabla y no enum porque el negocio añade unidades en ejecución, sin desplegar código.
 *
 * @param code   identificador de negocio, en mayúsculas
 * @param symbol abreviatura para mostrar, por ejemplo {@code kg}
 */
public record UnitOfMeasure(UUID id, String code, String name, String symbol) {

    private static final int CODE_MAX_LENGTH = 20;
    private static final int NAME_MAX_LENGTH = 80;
    private static final int SYMBOL_MAX_LENGTH = 20;

    public UnitOfMeasure {
        Objects.requireNonNull(id, "El identificador de la unidad no puede ser nulo.");
        code = requireText(code, "code", "El código de la unidad es obligatorio.", CODE_MAX_LENGTH)
                .toUpperCase(Locale.ROOT);
        name = requireText(name, "name", "El nombre de la unidad es obligatorio.", NAME_MAX_LENGTH);
        symbol = requireText(symbol, "symbol", "El símbolo de la unidad es obligatorio.", SYMBOL_MAX_LENGTH);
    }

    public static UnitOfMeasure create(String code, String name, String symbol) {
        return new UnitOfMeasure(UUID.randomUUID(), code, name, symbol);
    }

    private static String requireText(String value, String field, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainValidationException(field,
                    "No puede superar %d caracteres.".formatted(maxLength));
        }
        return normalized;
    }
}
