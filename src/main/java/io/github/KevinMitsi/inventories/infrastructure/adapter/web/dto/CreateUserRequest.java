package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Cuerpo de la petición de alta de un usuario (HU-02, HU-03).
 *
 * <p>Aquí sí se exige una longitud mínima de contraseña, a diferencia del formulario de
 * acceso: al crear una cuenta se está fijando la política, mientras que al entrar solo se
 * comprueba una contraseña que ya existe.
 */
@Schema(name = "CreateUserRequest", description = "Datos necesarios para dar de alta un usuario.")
public record CreateUserRequest(

        @Schema(description = """
                Sucursal a la que se asigna. **Obligatoria** para `BRANCH_MANAGER` e \
                `INVENTORY_OPERATOR`, que operan dentro de una sucursal concreta (RN-13). \
                Debe omitirse para `ADMIN`, cuyo ámbito es toda la organización (RN-12).""",
                example = "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d", nullable = true)
        UUID branchId,

        @Schema(description = "Rol, que determina sobre qué podrá operar el usuario.",
                example = "INVENTORY_OPERATOR", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "El rol es obligatorio.")
        RoleCode role,

        @Schema(description = "Nombre.", example = "Ana", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede superar {max} caracteres.")
        String firstName,

        @Schema(description = "Apellido.", example = "Torres", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El apellido es obligatorio.")
        @Size(max = 100, message = "El apellido no puede superar {max} caracteres.")
        String lastName,

        @Schema(description = "Correo electrónico. Es la credencial de acceso y debe ser "
                + "único dentro de la organización.",
                example = "ana.torres@optiplant.co", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico no tiene un formato válido.")
        @Size(max = 254, message = "El correo no puede superar {max} caracteres.")
        String email,

        @Schema(description = "Contraseña inicial. Se cifra antes de almacenarse y nunca se "
                + "guarda en claro.",
                example = "MiClaveSegura2026", requiredMode = Schema.RequiredMode.REQUIRED,
                format = "password", minLength = 8)
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 200,
                message = "La contraseña debe tener entre {min} y {max} caracteres.")
        String password
) {

    /** Enmascara la contraseña: este texto puede acabar en un log o en una traza. */
    @Override
    public String toString() {
        return "CreateUserRequest[email=%s, role=%s, branchId=%s, password=***]"
                .formatted(email, role, branchId);
    }
}
