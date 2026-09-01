package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.User;

import java.time.Duration;

/**
 * Puerto de salida para la emisión y verificación de tokens de sesión.
 *
 * <p>Aísla a la capa de aplicación de JJWT y del formato JWT en sí. El caso de uso de
 * autenticación pide "un token para este usuario" y recibe una cadena; cómo se firma, con
 * qué algoritmo y con qué estructura es asunto exclusivo del adaptador.
 */
public interface TokenProviderPort {

    /**
     * Emite un token de acceso para un usuario autenticado.
     *
     * <p>Incorpora rol, sucursal y organización, que es lo que permite resolver la
     * autorización sin ir a la base de datos en cada petición.
     */
    String generateAccessToken(User user);

    /**
     * Emite un token de renovación.
     *
     * <p>Vive mucho más que el de acceso pero <b>no autoriza ninguna operación</b>: solo
     * sirve para pedir un token de acceso nuevo. Esa separación es la que permite mantener
     * el token de acceso con una vida corta —y por tanto limitar el daño si se filtra— sin
     * obligar al usuario a introducir sus credenciales cada hora.
     */
    String generateRefreshToken(User user);

    /**
     * Verifica la firma y la vigencia de un token, y devuelve su contenido.
     *
     * <p>Un token con firma inválida, caducado o manipulado debe rechazarse, nunca
     * devolverse con los datos que afirma contener.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.InvalidTokenException
     *         si el token es ilegible, su firma no cuadra o ha caducado
     */
    TokenClaims parseAndValidate(String token);

    /** Tiempo de vida del token de acceso, que se informa al cliente al autenticarse. */
    Duration getAccessTokenTtl();
}
