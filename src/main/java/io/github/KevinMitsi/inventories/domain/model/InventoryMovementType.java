package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/**
 * Catálogo cerrado de motivos que pueden originar un movimiento de inventario.
 *
 * <p>Es un enum y no una tabla porque el conjunto está cerrado por diseño (ENTITIES.md §8.3,
 * {@code ck_inventory_movement_type} en V1) y porque la dirección de cada tipo es una regla
 * de negocio, no un dato: si viviera en una fila de base de datos, alguien podría insertar
 * {@code PURCHASE_IN} con dirección de salida y nada lo impediría.
 *
 * <p>{@link #PURCHASE_IN} y {@link #TRANSFER_IN} recalculan el costo promedio ponderado del
 * inventario (RF-23): son los únicos tipos que llegan acompañados de un costo unitario real
 * y verificable — el de la compra, o el heredado del saldo de origen al recibir una
 * transferencia.
 */
public enum InventoryMovementType {

    /** Entrada por recepción de una orden de compra (RN-05). */
    PURCHASE_IN(Direction.IN),

    /** Salida por confirmación de una venta (RN-06). */
    SALE_OUT(Direction.OUT),

    /** Entrada por recepción de una transferencia entre sucursales. */
    TRANSFER_IN(Direction.IN),

    /** Salida por despacho de una transferencia entre sucursales. */
    TRANSFER_OUT(Direction.OUT),

    /** Entrada manual: devolución, hallazgo u otro ingreso sin documento de origen. */
    RETURN_IN(Direction.IN),

    /** Salida manual: merma u otra baja sin documento de origen. */
    LOSS_OUT(Direction.OUT),

    /** Entrada generada al confirmar un ajuste de inventario formal (§18). */
    ADJUSTMENT_IN(Direction.IN),

    /** Salida generada al confirmar un ajuste de inventario formal (§18). */
    ADJUSTMENT_OUT(Direction.OUT);

    private final Direction direction;

    InventoryMovementType(Direction direction) {
        this.direction = direction;
    }

    public Direction direction() {
        return direction;
    }

    public boolean isEntry() {
        return direction == Direction.IN;
    }

    public boolean isExit() {
        return direction == Direction.OUT;
    }

    public static InventoryMovementType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("movementType", "El tipo de movimiento es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("movementType",
                    "Tipo de movimiento desconocido: '%s'.".formatted(value));
        }
    }

    /** Sentido del cambio de stock que produce el tipo de movimiento. */
    public enum Direction {
        IN, OUT
    }
}
