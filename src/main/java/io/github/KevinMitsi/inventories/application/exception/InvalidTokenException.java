package io.github.KevinMitsi.inventories.application.exception;

import io.github.KevinMitsi.inventories.domain.exception.DomainErrorCode;
import io.github.KevinMitsi.inventories.domain.exception.DomainException;

import java.io.Serial;
import java.util.Map;

/**
 * El token recibido no sirve para autenticar: está mal formado, su firma no cuadra, ha
 * caducado, o es de un tipo que no corresponde a la operación.
 *
 * <p>El motivo se transporta en un campo aparte y con valores acotados. Solo uno resulta
 * útil al cliente sin ser aprovechable por un atacante: saber que el token <em>caducó</em>
 * le indica a la aplicación que debe renovarlo en lugar de pedir credenciales otra vez. El
 * resto de causas —firma inválida, formato corrupto— no se detallan, porque describirían a
 * quien manipula un token en qué punto falló su intento.
 */
public class InvalidTokenException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Motivo del rechazo, en la medida en que es seguro revelarlo. */
    public enum Reason {

        /**
         * El token era legítimo pero ya no está vigente. Es la única causa que se comunica
         * con precisión, porque el cliente debe reaccionar renovando la sesión.
         */
        EXPIRED,

        /** Cualquier otro fallo: firma inválida, formato corrupto, emisor desconocido. */
        INVALID,

        /** Se presentó un token de renovación donde se esperaba uno de acceso, o al revés. */
        WRONG_TYPE
    }

    public InvalidTokenException(Reason reason) {
        super(DomainErrorCode.AUTHENTICATION_FAILED, messageFor(reason),
                Map.of("reason", reason.name()));
    }

    public InvalidTokenException(Reason reason, Throwable cause) {
        super(DomainErrorCode.AUTHENTICATION_FAILED, messageFor(reason), cause);
    }

    private static String messageFor(Reason reason) {
        return switch (reason) {
            case EXPIRED -> "La sesión ha expirado. Renueve el token o vuelva a autenticarse.";
            case WRONG_TYPE -> "El token presentado no es válido para esta operación.";
            case INVALID -> "El token de autenticación no es válido.";
        };
    }
}
