package io.github.KevinMitsi.inventories.application.exception;

import io.github.KevinMitsi.inventories.domain.exception.DomainErrorCode;
import io.github.KevinMitsi.inventories.domain.exception.DomainException;

import java.io.Serial;

/**
 * El intento de autenticación no prospera.
 *
 * <p><b>Un solo mensaje para todas las causas.</b> Da igual que el correo no exista, que la
 * contraseña no coincida o que la cuenta esté dada de baja: la respuesta es idéntica.
 * Distinguirlas convertiría el formulario de acceso en un oráculo con el que enumerar qué
 * direcciones están registradas, que es el primer paso de un ataque dirigido.
 *
 * <p>Por el mismo motivo la excepción no lleva detalles: cualquier dato que se adjuntara
 * acabaría publicado en el cuerpo de la respuesta. La causa concreta sí se registra en el
 * log del servidor, donde sirve para diagnosticar sin quedar expuesta.
 */
public class InvalidCredentialsException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String MESSAGE = "Las credenciales proporcionadas no son válidas.";

    public InvalidCredentialsException() {
        super(DomainErrorCode.AUTHENTICATION_FAILED, MESSAGE);
    }
}
