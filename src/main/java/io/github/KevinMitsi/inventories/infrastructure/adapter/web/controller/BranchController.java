package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ChangeBranchStatusUseCase;
import io.github.KevinMitsi.inventories.application.port.in.CreateBranchUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryBranchUseCase;
import io.github.KevinMitsi.inventories.application.port.in.UpdateBranchUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.BranchSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.BranchResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CreateBranchRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.UpdateBranchRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.BranchWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Adaptador de entrada HTTP para la gestión de sucursales (EP-02).
 *
 * <p>Su trabajo es exclusivamente de traducción y transporte: recibe la petición, la
 * convierte en un comando, invoca el caso de uso y transforma el resultado en una
 * respuesta. No contiene ni una regla de negocio (RNF-01), y esa es la razón de que sea
 * tan corto pese a cubrir seis operaciones.
 *
 * <p>Depende de las interfaces de caso de uso, no de {@code BranchService}. Podría
 * inyectarse la clase concreta —es la misma instancia— pero entonces el controlador
 * conocería métodos que no usa y la dirección de la dependencia apuntaría a una
 * implementación en vez de a un contrato.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Sucursales",
     description = """
             Gestión de las sucursales de la organización. Cada sucursal es una unidad \
             operativa autónoma: su inventario, sus ventas y sus compras le pertenecen, \
             y es uno de los dos extremos de toda transferencia.""")
public class BranchController {

    private final CreateBranchUseCase createBranchUseCase;
    private final UpdateBranchUseCase updateBranchUseCase;
    private final ChangeBranchStatusUseCase changeBranchStatusUseCase;
    private final QueryBranchUseCase queryBranchUseCase;
    private final BranchWebMapper mapper;

    // ----------------------------------------------------------------------------------
    // Creación
    // ----------------------------------------------------------------------------------

    @PostMapping(value = "/organizations/{organizationId}/branches",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createBranch",
            summary = "Registrar una sucursal",
            description = """
                    Da de alta una sucursal dentro de la organización indicada (HU-04, RF-05).

                    El código debe ser único dentro de la organización y se normaliza a \
                    mayúsculas, de modo que `bog-01` y `BOG-01` se consideran el mismo. \
                    Una vez creada, el código es inmutable porque aparece en documentos y \
                    referencias operativas.

                    La sucursal nace activa y lista para operar.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sucursal creada.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BranchResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "El cuerpo de la petición contiene campos con formato inválido.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La organización indicada no existe.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Ya existe una sucursal con ese código en la organización.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "Los datos son coherentes en formato pero incumplen un invariante del dominio.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<BranchResponse> createBranch(
            @Parameter(description = "Organización en la que se registra la sucursal.", required = true)
            @PathVariable UUID organizationId,

            @Valid @RequestBody CreateBranchRequest request) {

        Branch branch = createBranchUseCase.createBranch(mapper.toCommand(organizationId, request));

        // 201 con Location: el cliente obtiene la URL canónica del recurso recién creado
        // sin tener que componerla por su cuenta.
        URI location = UriComponentsBuilder.fromPath("/api/v1/branches/{id}")
                .buildAndExpand(branch.getId())
                .toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(branch));
    }

    // ----------------------------------------------------------------------------------
    // Consulta
    // ----------------------------------------------------------------------------------

    @GetMapping("/organizations/{organizationId}/branches")
    @Operation(
            operationId = "searchBranches",
            summary = "Listar sucursales de la organización",
            description = """
                    Devuelve las sucursales de la organización, filtradas y paginadas (HU-05, RF-06).

                    Sostiene una capacidad central del sistema: antes de solicitar una \
                    transferencia, un operador necesita saber qué sucursales existen y dónde \
                    puede haber inventario (HU-06). Por eso la consulta no se limita a la \
                    sucursal propia del usuario, a diferencia de las operaciones de escritura.

                    La respuesta siempre viene paginada. No existe una variante que devuelva \
                    todo el listado: el número de sucursales crece con la organización y una \
                    respuesta sin límite degradaría el tiempo de respuesta (RNF-07).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de sucursales.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Parámetros de paginación u ordenación inválidos.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<BranchResponse> searchBranches(
            @Parameter(description = "Organización cuyas sucursales se consultan.", required = true)
            @PathVariable UUID organizationId,

            @Parameter(description = "Búsqueda parcial e insensible a mayúsculas sobre código y nombre.",
                       example = "chapinero")
            @RequestParam(required = false) String text,

            @Parameter(description = "Filtra por ciudad exacta.", example = "Bogotá")
            @RequestParam(required = false) String city,

            @Parameter(description = "Filtra por estado de alta. Si se omite, devuelve activas e inactivas.",
                       example = "true")
            @RequestParam(required = false) Boolean active,

            @Parameter(description = "Índice de página, empezando en 0.", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @Parameter(description = "Elementos por página. El máximo es 100.", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size,

            @Parameter(description = "Campo de ordenación.",
                       schema = @Schema(allowableValues = {"code", "name", "city", "active",
                                                           "createdAt", "updatedAt"},
                                        defaultValue = "code"))
            @RequestParam(required = false) String sortBy,

            @Parameter(description = "Sentido de la ordenación.",
                       schema = @Schema(allowableValues = {"ASC", "DESC"}, defaultValue = "ASC"))
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        BranchSearchCriteria criteria =
                new BranchSearchCriteria(organizationId, text, city, active);

        PageQuery pageQuery =
                PageQuery.of(page, size, sortBy, SortDirection.fromString(sortDirection));

        PageResult<Branch> result = queryBranchUseCase.searchBranches(criteria, pageQuery);

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/branches/{branchId}")
    @Operation(
            operationId = "getBranchById",
            summary = "Consultar una sucursal",
            description = "Devuelve el detalle de una sucursal por su identificador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal encontrada.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BranchResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una sucursal con ese identificador.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BranchResponse getBranchById(
            @Parameter(description = "Identificador de la sucursal.", required = true)
            @PathVariable UUID branchId) {

        return mapper.toResponse(queryBranchUseCase.getBranchById(branchId));
    }

    // ----------------------------------------------------------------------------------
    // Modificación
    // ----------------------------------------------------------------------------------

    @PutMapping(value = "/branches/{branchId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateBranch",
            summary = "Actualizar los datos de una sucursal",
            description = """
                    Modifica nombre, dirección, ciudad, país y teléfono.

                    El código y la organización no se pueden cambiar: identifican a la \
                    sucursal y aparecen en documentos ya emitidos. Tampoco se cambia aquí \
                    el estado de alta, que tiene sus propias operaciones.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal actualizada.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BranchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una sucursal con ese identificador.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "La sucursal fue modificada por otra operación simultánea.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BranchResponse updateBranch(
            @Parameter(description = "Identificador de la sucursal.", required = true)
            @PathVariable UUID branchId,

            @Valid @RequestBody UpdateBranchRequest request) {

        Branch branch = updateBranchUseCase.updateBranch(mapper.toCommand(branchId, request));
        return mapper.toResponse(branch);
    }

    // ----------------------------------------------------------------------------------
    // Estado de alta
    // ----------------------------------------------------------------------------------

    @PostMapping("/branches/{branchId}/deactivation")
    @Operation(
            operationId = "deactivateBranch",
            summary = "Dar de baja una sucursal",
            description = """
                    Retira la sucursal de la operación. Deja de admitir ventas, compras, \
                    movimientos y transferencias nuevas.

                    Es una baja lógica, nunca un borrado. La sucursal aparece en ventas, \
                    compras y movimientos históricos, y eliminarla dejaría ese histórico sin \
                    poder explicarse (ENTITIES.md §30). Todo su pasado sigue siendo consultable.

                    La operación es idempotente: dar de baja una sucursal ya inactiva \
                    devuelve 200 y no produce ningún cambio.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal dada de baja.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BranchResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una sucursal con ese identificador.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.OK)
    public BranchResponse deactivateBranch(
            @Parameter(description = "Identificador de la sucursal.", required = true)
            @PathVariable UUID branchId) {

        return mapper.toResponse(changeBranchStatusUseCase.deactivateBranch(branchId));
    }

    @PostMapping("/branches/{branchId}/activation")
    @Operation(
            operationId = "activateBranch",
            summary = "Reactivar una sucursal",
            description = """
                    Devuelve a la operación una sucursal dada de baja.

                    Es idempotente: reactivar una sucursal ya activa devuelve 200 sin cambios.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal reactivada.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BranchResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una sucursal con ese identificador.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public BranchResponse activateBranch(
            @Parameter(description = "Identificador de la sucursal.", required = true)
            @PathVariable UUID branchId) {

        return mapper.toResponse(changeBranchStatusUseCase.activateBranch(branchId));
    }
}
