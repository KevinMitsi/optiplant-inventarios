package io.github.KevinMitsi.inventories.application.port.in.result;

import io.github.KevinMitsi.inventories.domain.model.User;

import java.time.Duration;

/**
 * Resultado de una autenticación correcta.
 *
 * <p>Devuelve el usuario del dominio, no un DTO: el adaptador web decide qué parte de él se
 * publica. Así el caso de uso queda disponible para cualquier otro adaptador sin quedar
 * atado a la forma de la respuesta HTTP.
 *
 * @param accessToken    token de vida corta que autoriza las operaciones
 * @param refreshToken   token de vida larga que solo sirve para renovar el de acceso
 * @param accessTokenTtl tiempo de vida del token de acceso, para que el cliente lo renueve
 *                       antes de que caduque en lugar de esperar a recibir un 401
 * @param user           usuario autenticado, con su rol y su sucursal
 */
public record AuthenticationResult(String accessToken,
                                   String refreshToken,
                                   Duration accessTokenTtl,
                                   User user) {

    /** Enmascara ambos tokens: equivalen a la sesión y no deben acabar en un log. */
    @Override
    public String toString() {
        return "AuthenticationResult[user=%s, accessToken=***, refreshToken=***]"
                .formatted(user == null ? null : user.getEmail());
    }
}
