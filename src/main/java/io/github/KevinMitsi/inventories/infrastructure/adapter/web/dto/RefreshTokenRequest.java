package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Petición de renovación de sesión a partir de un token de renovación. */
@Schema(name = "RefreshTokenRequest", description = "Token de renovación con el que obtener un token de acceso nuevo.")
public record RefreshTokenRequest(

        @Schema(description = """
                Token de renovación recibido al autenticarse. No sirve para autorizar \
                operaciones: solo para obtener un token de acceso nuevo.""",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El token de renovación es obligatorio.")
        String refreshToken
) {

    /** Enmascara el token: equivale a la sesión y no debe acabar en un log. */
    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=***]";
    }
}
