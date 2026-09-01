package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageLogisticsRouteUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryLogisticsRouteUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.LogisticsRouteSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.LogisticsRouteDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.LogisticsWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Rutas logísticas habituales entre sucursales, y su cumplimiento (EP-08, HU-36/37). */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Rutas logísticas", description = "Rutas habituales entre sucursales y su cumplimiento.")
public class LogisticsRouteController {

    private final ManageLogisticsRouteUseCase manageLogisticsRouteUseCase;
    private final QueryLogisticsRouteUseCase queryLogisticsRouteUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final LogisticsWebMapper mapper;

    @PostMapping(value = "/organizations/{organizationId}/logistics-routes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "createLogisticsRoute", summary = "Registrar una ruta logística",
            description = "Origen y destino deben ser sucursales distintas (RN-07), y la pareja "
                    + "origen/destino no puede repetirse.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ruta creada.",
                    content = @Content(schema = @Schema(implementation = LogisticsRouteDtos.LogisticsRouteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza, o la organización no es la suya.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La organización o alguna de las sucursales no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe una ruta con ese origen y ese destino.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "Origen y destino coinciden (RN-07), o la duración estimada no es positiva.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LogisticsRouteDtos.LogisticsRouteResponse> createRoute(
            @PathVariable UUID organizationId,
            @Valid @RequestBody LogisticsRouteDtos.CreateLogisticsRouteRequest request) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "crear rutas logísticas");

        LogisticsRoute route = manageLogisticsRouteUseCase.createRoute(mapper.toCommand(organizationId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/logistics-routes/{id}")
                .buildAndExpand(route.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(route));
    }

    @GetMapping("/organizations/{organizationId}/logistics-routes")
    @Operation(operationId = "searchLogisticsRoutes", summary = "Listar rutas logísticas",
            description = "Filtra por sucursal de origen, de destino y por estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de rutas.",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de paginación inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "La organización no es la del usuario.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<LogisticsRouteDtos.LogisticsRouteResponse> searchRoutes(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID originBranchId,
            @RequestParam(required = false) UUID destinationBranchId,
            @RequestParam(required = false) Boolean active,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar rutas logísticas");

        PageResult<LogisticsRoute> result = queryLogisticsRouteUseCase.searchRoutes(
                new LogisticsRouteSearchCriteria(organizationId, originBranchId, destinationBranchId, active),
                PageQuery.of(page, size));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/organizations/{organizationId}/logistics-routes/compliance")
    @Operation(operationId = "getRouteCompliance", summary = "Cumplimiento estimado vs. real por ruta (HU-36/37)",
            description = "Para cada ruta, compara la duración estimada con el tránsito real "
                    + "(despacho a recepción) de sus transferencias completadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cumplimiento por ruta. Una ruta sin transferencias "
                    + "completadas aparece sin tránsito real.",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = LogisticsRouteDtos.RouteComplianceResponse.class)))),
            @ApiResponse(responseCode = "403", description = "La organización no es la del usuario.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La organización no existe.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<LogisticsRouteDtos.RouteComplianceResponse> getRouteCompliance(@PathVariable UUID organizationId) {
        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar cumplimiento logístico");

        return queryLogisticsRouteUseCase.getRouteCompliance(organizationId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping("/logistics-routes/{routeId}")
    @Operation(operationId = "getLogisticsRouteById", summary = "Consultar una ruta logística")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ruta encontrada.",
                    content = @Content(schema = @Schema(implementation = LogisticsRouteDtos.LogisticsRouteResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una ruta con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public LogisticsRouteDtos.LogisticsRouteResponse getRoute(@PathVariable UUID routeId) {
        return mapper.toResponse(queryLogisticsRouteUseCase.getRouteById(routeId));
    }

    @PutMapping(value = "/logistics-routes/{routeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "updateLogisticsRoute", summary = "Actualizar una ruta logística",
            description = "Modifica nombre, duración estimada, costo y prioridad. El origen y el "
                    + "destino no son modificables: otra pareja de sucursales es otra ruta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ruta actualizada.",
                    content = @Content(schema = @Schema(implementation = LogisticsRouteDtos.LogisticsRouteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza la operación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una ruta con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "La duración estimada no es positiva.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public LogisticsRouteDtos.LogisticsRouteResponse updateRoute(
            @PathVariable UUID routeId,
            @Valid @RequestBody LogisticsRouteDtos.UpdateLogisticsRouteRequest request) {

        return mapper.toResponse(manageLogisticsRouteUseCase.updateRoute(mapper.toCommand(routeId, request)));
    }

    @PatchMapping("/logistics-routes/{routeId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "deactivateLogisticsRoute", summary = "Dar de baja una ruta logística",
            description = "Baja lógica: las transferencias que ya la usaron la conservan. Idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ruta dada de baja.",
                    content = @Content(schema = @Schema(implementation = LogisticsRouteDtos.LogisticsRouteResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza la operación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una ruta con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public LogisticsRouteDtos.LogisticsRouteResponse deactivateRoute(@PathVariable UUID routeId) {
        return mapper.toResponse(manageLogisticsRouteUseCase.deactivateRoute(routeId));
    }

    @PatchMapping("/logistics-routes/{routeId}/activation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "activateLogisticsRoute", summary = "Reactivar una ruta logística")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ruta reactivada.",
                    content = @Content(schema = @Schema(implementation = LogisticsRouteDtos.LogisticsRouteResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza la operación.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una ruta con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public LogisticsRouteDtos.LogisticsRouteResponse activateRoute(@PathVariable UUID routeId) {
        return mapper.toResponse(manageLogisticsRouteUseCase.activateRoute(routeId));
    }
}
