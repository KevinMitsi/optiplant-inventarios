package io.github.KevinMitsi.inventories.domain.exception;

/**
 * Clasificación de los fallos que puede producir el dominio.
 *
 * <p>Existe para que el dominio pueda expresar <em>qué</em> salió mal sin conocer el
 * protocolo por el que se expone. La traducción a un código de estado HTTP ocurre en
 * la capa de infraestructura, en el manejador global de excepciones. Gracias a eso el
 * mismo dominio podría exponerse mañana por otro transporte sin tocar una línea.
 */
public enum DomainErrorCode {

    /** La entidad solicitada no existe o no es visible para el solicitante. */
    RESOURCE_NOT_FOUND,

    /** Vulnera una restricción de unicidad: SKU, código de sucursal, email, etc. */
    DUPLICATE_RESOURCE,

    /** Incumple una regla de negocio explícita del catálogo RN-01..RN-13. */
    BUSINESS_RULE_VIOLATION,

    /** El stock disponible no cubre la cantidad pedida (RN-03, RN-08). */
    INSUFFICIENT_STOCK,

    /** La transición de estado solicitada no está permitida por el ciclo de vida. */
    INVALID_STATE_TRANSITION,

    /** Otra transacción modificó el mismo registro primero (bloqueo optimista, RNF-05). */
    CONCURRENT_MODIFICATION,

    /** El rol del solicitante no le autoriza a operar sobre ese ámbito (RN-12, RN-13). */
    OPERATION_NOT_PERMITTED,

    /** Los datos de entrada son inconsistentes entre sí más allá de la validación de formato. */
    VALIDATION_ERROR
}
