package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/**
 * Ciclo de vida de una transferencia entre sucursales (ENTITIES.md §13.1).
 *
 * <p>{@code ISSUE_PENDING} existe en el catálogo de la base (y en el {@code CHECK} de
 * {@code transfer}) pero el dominio no lo produce: una recepción con faltante deja la
 * transferencia en {@code PARTIALLY_RECEIVED} y abre un {@link TransferIssue} ahí mismo,
 * sin un estado intermedio adicional. Es una simplificación documentada del MVP —
 * {@code ISSUE_PENDING} queda reservado para diferenciar, en el futuro, una incidencia
 * detectada fuera de la recepción (por ejemplo, avería reportada después) de una recepción
 * simplemente incompleta.
 */
public enum TransferStatus {

    /** Recién solicitada por la sucursal de origen (HU-27). */
    REQUESTED,

    /** Aprobada, con cantidades por línea ya confirmadas o ajustadas (HU-29). */
    APPROVED,

    /** En preparación física en la sucursal de origen. */
    IN_PREPARATION,

    /** Despachada: descontó inventario de origen mediante {@code TRANSFER_OUT} (RN-08). */
    IN_TRANSIT,

    /** Recibida, pero al menos una línea llegó incompleta (RN-09, RN-10). */
    PARTIALLY_RECEIVED,

    /** Recibida por completo: cada línea llegó en la cantidad despachada. */
    RECEIVED,

    /** Cancelada antes de despachar. */
    CANCELLED,

    /** Cerrada tras resolver todas las incidencias abiertas de una recepción parcial. */
    CLOSED;

    public static TransferStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("status", "El estado de la transferencia es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("status",
                    "Estado de transferencia desconocido: '%s'.".formatted(value));
        }
    }
}
