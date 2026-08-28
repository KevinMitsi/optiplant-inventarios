package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/**
 * Cómo se resolvió una incidencia de transferencia (ENTITIES.md §15.2).
 *
 * <p>Es una etiqueta descriptiva, no un disparador de efectos: {@code RESHIPMENT} no crea
 * una transferencia nueva ni {@code ADJUSTMENT} un ajuste de inventario formal. Automatizar
 * esas consecuencias queda fuera del MVP (misma simplificación documentada que el resto del
 * ciclo de transferencias).
 */
public enum TransferIssueResolution {

    RESHIPMENT,
    ADJUSTMENT,
    CLAIM;

    public static TransferIssueResolution fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("resolutionType", "El tipo de resolución es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("resolutionType",
                    "Tipo de resolución desconocido: '%s'.".formatted(value));
        }
    }
}
