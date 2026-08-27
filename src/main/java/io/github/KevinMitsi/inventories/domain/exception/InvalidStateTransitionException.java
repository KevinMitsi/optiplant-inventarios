package io.github.KevinMitsi.inventories.domain.exception;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Se intentó llevar un documento a un estado al que no puede llegar desde el actual.
 *
 * <p>Ejemplos: despachar una transferencia que nadie aprobó, recibir una que todavía no
 * salió, o confirmar una orden de compra ya cancelada. La lista de transiciones válidas
 * la define cada enum de estado en el dominio, no el servicio, de modo que la regla
 * viaja con el propio tipo y no puede olvidarse en una ruta nueva.
 */
public class InvalidStateTransitionException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidStateTransitionException(String entity, Enum<?> current, Enum<?> target) {
        super(DomainErrorCode.INVALID_STATE_TRANSITION,
                "%s no puede pasar de '%s' a '%s'.".formatted(entity, current, target),
                details(entity, current.name(), target.name(), null));
    }

    public InvalidStateTransitionException(String entity, Enum<?> current, Enum<?> target, String reason) {
        super(DomainErrorCode.INVALID_STATE_TRANSITION,
                "%s no puede pasar de '%s' a '%s': %s".formatted(entity, current, target, reason),
                details(entity, current.name(), target.name(), reason));
    }

    /** Rechaza una operación por el estado actual, sin que exista un estado destino concreto. */
    public InvalidStateTransitionException(String entity, Enum<?> current, String operation) {
        super(DomainErrorCode.INVALID_STATE_TRANSITION,
                "No se puede %s: %s se encuentra en estado '%s'.".formatted(operation, entity, current),
                details(entity, current.name(), null, operation));
    }

    private static Map<String, Object> details(String entity, String current, String target, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("entity", entity);
        details.put("currentState", current);
        if (target != null) {
            details.put("targetState", target);
        }
        if (reason != null) {
            details.put("reason", reason);
        }
        return details;
    }
}
