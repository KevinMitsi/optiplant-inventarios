package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/**
 * Severidad de una entrada de la traza de auditoría.
 *
 * <p>Son tres y no los siete niveles de {@code java.util.logging} a propósito: la traza es
 * un registro de negocio, no un canal de depuración. {@code FINE} y por debajo no llegan a
 * la base de datos —serían ruido caro de almacenar— y los niveles superiores se agrupan en
 * estas tres categorías, que es la granularidad con la que un auditor filtra de verdad.
 */
public enum ActivityLogLevel {

    /** Operación de negocio completada con normalidad. */
    INFO,

    /** Algo no salió como estaba previsto pero la operación siguió adelante. */
    WARNING,

    /** Fallo que impidió completar la operación. */
    SEVERE;

    public static ActivityLogLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("level", "El nivel del registro es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("level",
                    "Nivel de registro desconocido: '%s'.".formatted(value));
        }
    }
}
