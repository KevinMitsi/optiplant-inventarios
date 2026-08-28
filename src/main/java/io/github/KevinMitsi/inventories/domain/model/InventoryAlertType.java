package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/**
 * Tipo de alerta de reabastecimiento (RF-16, funcionalidad adicional §34).
 *
 * <p>El MVP solo dispara {@link #LOW_STOCK} y {@link #OUT_OF_STOCK}; {@link #OVERSTOCK}
 * queda declarado porque el esquema ya lo admite ({@code ck_inventory_alert_type} en V1),
 * pero ningún flujo actual lo produce.
 */
public enum InventoryAlertType {
    LOW_STOCK,
    OUT_OF_STOCK,
    OVERSTOCK;

    public static InventoryAlertType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("alertType", "El tipo de alerta es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("alertType",
                    "Tipo de alerta desconocido: '%s'.".formatted(value));
        }
    }
}
