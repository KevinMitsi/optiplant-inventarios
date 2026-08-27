package io.github.KevinMitsi.inventories.application.exception;

import io.github.KevinMitsi.inventories.domain.exception.DomainErrorCode;
import io.github.KevinMitsi.inventories.domain.exception.DomainException;

import java.io.Serial;
import java.util.Map;

/**
 * Ya existe otra entidad con esa clave natural: SKU dentro de la organización, código de
 * sucursal, email de usuario, número de orden dentro de la sucursal, etc.
 *
 * <p>El servicio comprueba la unicidad antes de escribir para poder dar un mensaje útil,
 * pero el índice único de la base de datos sigue siendo la garantía real frente a dos
 * peticiones concurrentes; esa violación también acaba traducida a esta excepción.
 */
public class DuplicateResourceException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateResourceException(String resource, String field, Object value) {
        super(DomainErrorCode.DUPLICATE_RESOURCE,
                "Ya existe %s con %s '%s'.".formatted(resource, field, value),
                Map.of("resource", resource, "field", field, "value", String.valueOf(value)));
    }

    public DuplicateResourceException(String message, Map<String, Object> details) {
        super(DomainErrorCode.DUPLICATE_RESOURCE, message, details);
    }
}
