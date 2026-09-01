package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Representación de un usuario en las respuestas de la API.
 *
 * <p><b>No contiene el hash de la contraseña ni ningún campo derivado de ella.</b> Que sea
 * una clase distinta del modelo de dominio es precisamente lo que hace imposible filtrarlo
 * por descuido: para que apareciera en una respuesta habría que añadirlo aquí a propósito.
 * Si se serializara el agregado directamente, bastaría con olvidar una anotación.
 */
@Schema(name = "UserResponse", description = "Usuario del sistema, con su rol y su ámbito de operación.")
public record UserResponse(

        @Schema(description = "Identificador único del usuario.",
                example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID id,

        @Schema(description = "Organización a la que pertenece.",
                example = "9a8b7c6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d")
        UUID organizationId,

        @Schema(description = """
                Sucursal asignada. Nulo para el administrador general, cuyo ámbito \
                de operación es toda la organización (RN-12).""",
                example = "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                nullable = true)
        UUID branchId,

        @Schema(description = "Rol, que determina sobre qué puede operar el usuario.",
                example = "BRANCH_MANAGER")
        RoleCode role,

        @Schema(description = "Nombre legible del rol.", example = "Gerente de sucursal")
        String roleName,

        @Schema(description = "Nombre.", example = "Ana")
        String firstName,

        @Schema(description = "Apellido.", example = "Torres")
        String lastName,

        @Schema(description = "Correo electrónico, que es también la credencial de acceso.",
                example = "ana.torres@optiplant.co")
        String email,

        @Schema(description = """
                Indica si la cuenta está habilitada. Una cuenta dada de baja no puede \
                autenticarse, pero conserva su histórico de movimientos (RN-11).""",
                example = "true")
        boolean active,

        @Schema(description = "Último acceso correcto, en UTC.",
                example = "2026-08-27T08:14:03Z", nullable = true)
        Instant lastLoginAt,

        @Schema(description = "Fecha de creación, en UTC.", example = "2026-01-15T09:30:00Z")
        Instant createdAt,

        @Schema(description = "Fecha de la última modificación, en UTC.",
                example = "2026-08-27T14:05:22Z")
        Instant updatedAt
) {
}
