package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/**
 * Naturaleza de una incidencia de transferencia (ENTITIES.md §15.1).
 *
 * <p>El dominio solo genera {@link #MISSING} automáticamente, al recibir menos de lo
 * despachado (RN-10). {@code DAMAGED}, {@code WRONG_PRODUCT} y {@code OTHER} quedan en el
 * catálogo para un reporte manual de incidencias que no forma parte de este MVP —
 * simplificación documentada, igual que el reenvío real tras resolver una incidencia.
 */
public enum TransferIssueType {

    MISSING,
    DAMAGED,
    WRONG_PRODUCT,
    OTHER;

    public static TransferIssueType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("issueType", "El tipo de incidencia es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("issueType",
                    "Tipo de incidencia desconocido: '%s'.".formatted(value));
        }
    }
}
