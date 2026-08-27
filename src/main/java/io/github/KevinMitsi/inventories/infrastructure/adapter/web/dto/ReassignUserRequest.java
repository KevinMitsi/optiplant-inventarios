package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Cuerpo de la petición de reasignación de rol y sucursal (HU-03, RF-04).
 *
 * <p>Ambos viajan juntos porque están acoplados: promover a administrador general libera la
 * sucursal, y dejar de serlo obliga a asignar una. Permitirlos por separado dejaría estados
 * intermedios inválidos, como un gerente sin sucursal, incapaz de operar.
 */
@Schema(name = "ReassignUserRequest",
        description = "Nuevo rol y nueva sucursal de un usuario. Ambos cambian a la vez.")
public record ReassignUserRequest(

        @Schema(description = "Rol destino.", example = "BRANCH_MANAGER",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "El rol es obligatorio.")
        RoleCode role,

        @Schema(description = """
                Sucursal destino. **Obligatoria** si el rol es `BRANCH_MANAGER` o \
                `INVENTORY_OPERATOR`; debe omitirse si el rol es `ADMIN`.""",
                example = "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d", nullable = true)
        UUID branchId
) {
}
