package io.github.KevinMitsi.inventories.domain.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Los datos son válidos por separado pero incoherentes entre sí.
 *
 * <p>Jakarta Validation cubre lo que se puede juzgar mirando un campo aislado: obligatorio,
 * longitud, rango, formato. Esta excepción cubre lo que solo se ve al comparar varios
 * campos o al contrastar con el estado ya persistido: una fecha de fin anterior a la de
 * inicio, un factor de conversión que contradice a la unidad base del producto, una
 * cantidad recibida mayor que la despachada.
 *
 * <p>Se responde con 422 y no con 400: la petición está bien formada, lo que falla es su
 * consistencia semántica.
 */
public class DomainValidationException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DomainValidationException(String message) {
        super(DomainErrorCode.VALIDATION_ERROR, message);
    }

    public DomainValidationException(String field, String message) {
        super(DomainErrorCode.VALIDATION_ERROR, message, Map.of("field", field));
    }

    public DomainValidationException(String message, Map<String, Object> details) {
        super(DomainErrorCode.VALIDATION_ERROR, message, details);
    }
}
