package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta a una autenticación o a una renovación correctas.
 *
 * <p>Incluye el usuario para que el cliente pueda pintar la interfaz de inmediato —su rol y
 * su sucursal determinan qué opciones mostrar— sin encadenar una segunda petición.
 */
@Schema(name = "AuthenticationResponse", description = "Tokens de sesión y datos del usuario autenticado.")
public record AuthenticationResponse(

        @Schema(description = """
                Token de acceso. Se envía en la cabecera `Authorization` con el prefijo \
                `Bearer` en cada petición posterior.""",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3YzllNjY3OS03NDI1LTQwZGUifQ.abc123")
        String accessToken,

        @Schema(description = """
                Token de renovación. **No autoriza operaciones**: solo sirve para obtener \
                un token de acceso nuevo cuando el actual caduca. Vive mucho más, así que \
                debe guardarse con más cuidado que el de acceso.""",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3YzllNjY3OS03NDI1LTQwZGUifQ.xyz789")
        String refreshToken,

        @Schema(description = "Esquema de autenticación a usar en la cabecera.", example = "Bearer")
        String tokenType,

        @Schema(description = """
                Segundos que el token de acceso seguirá siendo válido. Permite al cliente \
                renovarlo antes de que caduque, en lugar de esperar a recibir un 401.""",
                example = "3600")
        long expiresIn,

        @Schema(description = "Usuario autenticado, con su rol y su sucursal.")
        UserResponse user
) {

    /** Enmascara ambos tokens: equivalen a la sesión y no deben acabar en un log. */
    @Override
    public String toString() {
        return "AuthenticationResponse[tokenType=%s, expiresIn=%d, user=%s, tokens=***]"
                .formatted(tokenType, expiresIn, user == null ? null : user.email());
    }
}
