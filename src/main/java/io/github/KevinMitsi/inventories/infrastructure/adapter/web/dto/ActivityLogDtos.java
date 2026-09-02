package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Contratos HTTP de la traza de auditoría. */
public final class ActivityLogDtos {

    private ActivityLogDtos() {
    }

    @Schema(name = "ActivityLogResponse",
            description = "Entrada de la traza de auditoría: qué se hizo, cuándo, quién y con qué rol.")
    public record ActivityLogResponse(

            @Schema(description = "Identificador único de la entrada.")
            UUID id,

            @Schema(description = "Instante en que ocurrió la operación, en UTC.",
                    example = "2026-09-02T14:35:12.482Z")
            Instant occurredAt,

            @Schema(description = "Correo del usuario que realizó la operación, o 'sistema' "
                    + "si no hubo una petición autenticada detrás.",
                    example = "gerente@optiplant.co")
            String username,

            @Schema(description = "Usuario que realizó la operación. Nulo en registros del sistema.")
            UUID userId,

            @Schema(description = "Organización en cuyo contexto ocurrió. Nula en registros del sistema.")
            UUID organizationId,

            @Schema(description = "Rol con el que actuó, tal como estaba en ese momento.",
                    allowableValues = {"ADMIN", "BRANCH_MANAGER", "INVENTORY_OPERATOR", "SYSTEM"},
                    example = "BRANCH_MANAGER")
            String role,

            @Schema(description = "Caso de uso que emitió el registro.", example = "CategoryUseCase")
            String useCase,

            @Schema(description = "Descripción de la operación.",
                    example = "Categoría creada: id=1f5f..., código=BEB")
            String operation,

            @Schema(description = "Severidad del registro.",
                    allowableValues = {"INFO", "WARNING", "SEVERE"}, example = "INFO")
            String level,

            @Schema(description = "Indica si la originó el propio sistema y no un usuario.",
                    example = "false")
            boolean systemGenerated
    ) {
    }
}
