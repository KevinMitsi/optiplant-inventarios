package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición de modificación de los datos personales.
 *
 * <p>No admite correo, rol, sucursal ni contraseña: cada uno tiene consecuencias propias y
 * su propia operación. Dejarlos fuera del contrato hace que ni siquiera se puedan intentar
 * cambiar por esta vía.
 */
@Schema(name = "UpdateUserProfileRequest", description = "Datos personales modificables de un usuario.")
public record UpdateUserProfileRequest(

        @Schema(description = "Nombre.", example = "Ana María", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede superar {max} caracteres.")
        String firstName,

        @Schema(description = "Apellido.", example = "Torres Rojas", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El apellido es obligatorio.")
        @Size(max = 100, message = "El apellido no puede superar {max} caracteres.")
        String lastName
) {
}
