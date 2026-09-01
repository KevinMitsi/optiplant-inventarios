package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/** Ciclo de vida de una venta (ENTITIES.md §11.1). */
public enum SaleStatus {

    /** Recién creada; todavía no descuenta inventario. */
    DRAFT,

    /** Confirmada: descontó inventario mediante movimientos {@code SALE_OUT} (RN-06). */
    CONFIRMED,

    /** Cancelada. Si venía de {@code CONFIRMED}, el inventario se restituyó por completo. */
    CANCELLED;

    public static SaleStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("status", "El estado de la venta es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("status", "Estado de venta desconocido: '%s'.".formatted(value));
        }
    }
}
