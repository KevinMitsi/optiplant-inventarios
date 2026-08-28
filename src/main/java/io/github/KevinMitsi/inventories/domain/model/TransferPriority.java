package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/** Urgencia con la que debe atenderse una transferencia (ENTITIES.md §13.2). */
public enum TransferPriority {

    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public static TransferPriority fromString(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("priority",
                    "Prioridad de transferencia desconocida: '%s'.".formatted(value));
        }
    }
}
