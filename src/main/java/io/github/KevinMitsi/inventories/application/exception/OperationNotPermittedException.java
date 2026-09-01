package io.github.KevinMitsi.inventories.application.exception;

import io.github.KevinMitsi.inventories.domain.exception.DomainErrorCode;
import io.github.KevinMitsi.inventories.domain.exception.DomainException;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El solicitante está autenticado, pero su rol o su sucursal no le permiten esta operación.
 *
 * <p>Cubre la autorización que depende de datos y que por tanto no puede resolverse solo
 * con anotaciones de método: un gerente opera dentro de su propia sucursal (RN-13),
 * mientras que el administrador general las ve todas (RN-12). Saber si la operación
 * está permitida exige comparar la sucursal del recurso con la del usuario, y eso solo
 * se puede hacer una vez cargado el recurso.
 *
 * <p>Es autorización, no autenticación: se traduce a 403, nunca a 401.
 */
public class OperationNotPermittedException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OperationNotPermittedException(String message) {
        super(DomainErrorCode.OPERATION_NOT_PERMITTED, message);
    }

    public OperationNotPermittedException(String operation, String reason) {
        super(DomainErrorCode.OPERATION_NOT_PERMITTED,
                "No está autorizado para %s: %s".formatted(operation, reason),
                details(operation, reason));
    }

    private static Map<String, Object> details(String operation, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operation", operation);
        details.put("reason", reason);
        return details;
    }
}
