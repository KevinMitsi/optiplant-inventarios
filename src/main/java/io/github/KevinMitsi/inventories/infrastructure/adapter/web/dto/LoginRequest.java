package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de acceso (HU-01).
 *
 * <p>La validación es deliberadamente mínima —solo obligatoriedad y longitud— y no impone
 * reglas de complejidad sobre la contraseña. Comprobar aquí que tiene mayúsculas o símbolos
 * revelaría la política de contraseñas a quien intenta adivinarlas, sin aportar seguridad:
 * la contraseña ya existe o no existe, y este formulario solo la comprueba.
 */
@Schema(name = "LoginRequest", description = "Credenciales para iniciar sesión.")
public record LoginRequest(

        @Schema(description = "Correo electrónico del usuario.",
                example = "ana.torres@optiplant.co",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico no tiene un formato válido.")
        @Size(max = 254, message = "El correo no puede superar {max} caracteres.")
        String email,

        @Schema(description = "Contraseña del usuario.",
                example = "MiClaveSegura2026",
                requiredMode = Schema.RequiredMode.REQUIRED,
                format = "password")
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 200, message = "La contraseña no puede superar {max} caracteres.")
        String password
) {

    /** Enmascara la contraseña: este texto puede acabar en un log o en una traza. */
    @Override
    public String toString() {
        return "LoginRequest[email=%s, password=***]".formatted(email);
    }
}
