package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/** Ciclo de vida de una orden de compra (ENTITIES.md §10.1). */
public enum PurchaseOrderStatus {

    /** Recién creada; sus líneas todavía pueden considerarse una propuesta. */
    DRAFT,

    /** Confirmada con el proveedor; a partir de aquí puede empezar a recibirse mercancía. */
    CONFIRMED,

    /** Se recibió parte de la mercancía, pero no la totalidad de cada línea. */
    PARTIALLY_RECEIVED,

    /** Toda la mercancía de todas las líneas fue recibida. */
    RECEIVED,

    /** Cancelada antes de recibir nada. */
    CANCELLED;

    public boolean isOpen() {
        return this == DRAFT || this == CONFIRMED || this == PARTIALLY_RECEIVED;
    }

    public static PurchaseOrderStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("status", "El estado de la orden es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("status", "Estado de orden desconocido: '%s'.".formatted(value));
        }
    }
}
