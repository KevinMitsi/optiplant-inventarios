package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/** Ciclo de vida de una alerta de reabastecimiento (ENTITIES.md §17.2). */
public enum InventoryAlertStatus {

    /** Disparada y vigente: la condición que la originó sigue presente. */
    OPEN,

    /** La condición se superó — el stock volvió a superar el mínimo. */
    RESOLVED,

    /** Un usuario la descartó manualmente sin que cambiara el stock. */
    DISMISSED;

    public boolean isOpen() {
        return this == OPEN;
    }

    public static InventoryAlertStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("status", "El estado de la alerta es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("status",
                    "Estado de alerta desconocido: '%s'.".formatted(value));
        }
    }
}
