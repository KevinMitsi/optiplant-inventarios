package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición de cambio de contraseña.
 *
 * <p>Exige la contraseña actual además de la nueva. No es redundante con estar autenticado:
 * protege frente a que un token robado, o una sesión dejada abierta en un equipo compartido,
 * basten para apoderarse de la cuenta cambiándole la clave.
 */
@Schema(name = "ChangePasswordRequest", description = "Cambio de contraseña del propio usuario.")
public record ChangePasswordRequest(

        @Schema(description = "Contraseña actual, que debe verificarse antes de aplicar el cambio.",
                requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
        @NotBlank(message = "La contraseña actual es obligatoria.")
        @Size(max = 200, message = "La contraseña no puede superar {max} caracteres.")
        String currentPassword,

        @Schema(description = "Contraseña nueva.", requiredMode = Schema.RequiredMode.REQUIRED,
                format = "password", minLength = 8)
        @NotBlank(message = "La contraseña nueva es obligatoria.")
        @Size(min = 8, max = 200,
                message = "La contraseña debe tener entre {min} y {max} caracteres.")
        String newPassword
) {

    /** Enmascara ambas contraseñas: este texto puede acabar en un log o en una traza. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
