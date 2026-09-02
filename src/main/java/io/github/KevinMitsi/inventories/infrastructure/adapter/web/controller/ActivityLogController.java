package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.QueryActivityLogUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.ActivityLogSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ActivityLogDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.ActivityLogWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Auditoría",
     description = "Traza de lo ocurrido en el sistema: fecha, usuario, rol y operación. "
             + "Se alimenta sola de lo que registran los casos de uso; no hay forma de "
             + "escribir ni de modificar una entrada desde la API.")
public class ActivityLogController {

    private final QueryActivityLogUseCase queryActivityLogUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final ActivityLogWebMapper mapper;

    @GetMapping("/organizations/{organizationId}/activity-logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "searchActivityLogs", summary = "Consultar la traza de auditoría",
            description = "Devuelve las entradas de la traza de la organización, filtradas y "
                    + "paginadas. Sin ordenación explícita se devuelven de la más reciente a la "
                    + "más antigua, que es el orden útil para auditar.\n\n"
                    + "**Solo para el administrador**: la traza revela quién hizo qué en toda la "
                    + "organización, incluidas las sucursales ajenas al solicitante.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de entradas de la traza.",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de paginación u ordenación inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza, o la organización no es la suya.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La organización no existe.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "La fecha inicial es posterior a la final.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<ActivityLogDtos.ActivityLogResponse> searchActivityLogs(
            @PathVariable UUID organizationId,

            @Parameter(description = "Correo exacto del usuario que realizó la operación.")
            @RequestParam(required = false) String username,

            @Parameter(description = "Rol con el que se actuó.",
                    schema = @Schema(allowableValues = {"ADMIN", "BRANCH_MANAGER",
                            "INVENTORY_OPERATOR", "SYSTEM"}))
            @RequestParam(required = false) String role,

            @Parameter(description = "Búsqueda parcial sobre el caso de uso emisor.", example = "Sale")
            @RequestParam(required = false) String useCase,

            @Parameter(description = "Severidad del registro.",
                    schema = @Schema(allowableValues = {"INFO", "WARNING", "SEVERE"}))
            @RequestParam(required = false) String level,

            @Parameter(description = "Búsqueda parcial sobre la descripción de la operación.")
            @RequestParam(required = false) String text,

            @Parameter(description = "Límite inferior de fecha, inclusive (ISO-8601).")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,

            @Parameter(description = "Límite superior de fecha, inclusive (ISO-8601).")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,

            @Parameter(description = "Añade los registros del propio sistema, que no pertenecen "
                    + "a ninguna organización (arranque, tareas internas).")
            @RequestParam(defaultValue = "false") boolean includeSystem,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size,

            @Parameter(description = "Campo de ordenación. Si se omite, se ordena por fecha descendente.",
                    schema = @Schema(allowableValues = {"occurredAt", "username", "role",
                            "useCase", "level"}))
            @RequestParam(required = false) String sortBy,

            @Parameter(schema = @Schema(allowableValues = {"ASC", "DESC"}, defaultValue = "DESC"))
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar la traza de auditoría");

        PageResult<ActivityLog> result = queryActivityLogUseCase.searchActivityLogs(
                new ActivityLogSearchCriteria(organizationId, username, role, useCase, level,
                        text, from, to, includeSystem),
                PageQuery.of(page, size, sortBy, SortDirection.fromString(sortDirection)));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/activity-logs/{activityLogId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "getActivityLogById", summary = "Consultar una entrada de la traza")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada encontrada.",
                    content = @Content(schema = @Schema(implementation = ActivityLogDtos.ActivityLogResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza, o la entrada es de otra organización.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una entrada con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ActivityLogDtos.ActivityLogResponse getActivityLogById(@PathVariable UUID activityLogId) {
        ActivityLog activityLog = queryActivityLogUseCase.getActivityLogById(activityLogId);

        // Los registros del sistema no pertenecen a ninguna organización; el resto solo se
        // muestran al administrador de la suya.
        if (activityLog.organizationId() != null) {
            currentUserProvider.requireBelongsToOrganization(
                    activityLog.organizationId(), "consultar la traza de auditoría");
        }

        return mapper.toResponse(activityLog);
    }
}
