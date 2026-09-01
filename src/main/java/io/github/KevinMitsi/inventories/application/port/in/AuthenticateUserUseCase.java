package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.AuthenticationCommand;
import io.github.KevinMitsi.inventories.application.port.in.result.AuthenticationResult;

/**
 * Acceso al sistema y renovación de la sesión (HU-01, RF-01).
 */
public interface AuthenticateUserUseCase {

    /**
     * Verifica las credenciales y emite los tokens de sesión.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.InvalidCredentialsException
     *         si el correo no existe, la contraseña no coincide o la cuenta está dada de
     *         baja. Las tres causas producen la misma excepción a propósito, para no
     *         convertir el formulario de acceso en un medio de averiguar qué direcciones
     *         están registradas.
     */
    AuthenticationResult authenticate(AuthenticationCommand command);

    /**
     * Emite un token de acceso nuevo a partir de uno de renovación.
     *
     * <p>Vuelve a cargar el usuario desde la base en lugar de fiarse de lo que afirma el
     * token. Es lo que hace efectiva una baja: el token de renovación de una cuenta
     * desactivada sigue siendo criptográficamente válido, pero deja de servir en cuanto se
     * comprueba el estado real del usuario.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.InvalidTokenException
     *         si el token es inválido, ha caducado o es de acceso en lugar de renovación
     */
    AuthenticationResult refresh(String refreshToken);
}
