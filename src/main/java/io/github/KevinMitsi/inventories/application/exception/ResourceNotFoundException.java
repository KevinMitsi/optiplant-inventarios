package io.github.KevinMitsi.inventories.application.exception;

import io.github.KevinMitsi.inventories.domain.exception.DomainErrorCode;
import io.github.KevinMitsi.inventories.domain.exception.DomainException;

import java.io.Serial;
import java.util.Map;

/**
 * La entidad pedida no existe.
 *
 * <p>También se lanza cuando la entidad existe pero queda fuera del ámbito visible para
 * quien pregunta. Distinguir "no existe" de "existe pero no es tuya" filtraría la
 * existencia de registros de otras sucursales, así que ambos casos se responden igual.
 */
public class ResourceNotFoundException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resource, Object identifier) {
        super(DomainErrorCode.RESOURCE_NOT_FOUND,
                "No se encontró %s con identificador '%s'.".formatted(resource, identifier),
                Map.of("resource", resource, "identifier", String.valueOf(identifier)));
    }

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(DomainErrorCode.RESOURCE_NOT_FOUND,
                "No se encontró %s con %s '%s'.".formatted(resource, field, value),
                Map.of("resource", resource, "field", field, "value", String.valueOf(value)));
    }
}
