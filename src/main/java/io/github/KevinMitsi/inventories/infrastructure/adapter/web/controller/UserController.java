package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.exception.OperationNotPermittedException;
import io.github.KevinMitsi.inventories.application.port.in.ManageUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.UserSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.AuthenticatedUser;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ChangePasswordRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CreateUserRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ReassignUserRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.UpdateUserProfileRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.UserResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.UserWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Adaptador de entrada HTTP para la administración de usuarios (EP-01, RF-03, RF-04).
 *
 * <p><b>La autorización se aplica en dos niveles complementarios</b>, y hacen falta los dos:
 * <ul>
 *   <li>{@code @PreAuthorize} resuelve lo que depende solo del rol —"esto lo hace un
 *       administrador"— antes incluso de entrar al método.</li>
 *   <li>{@link CurrentUserProvider} resuelve lo que depende de los datos: si el recurso
 *       pertenece a la organización del solicitante, o si el usuario es él mismo. Eso no se
 *       puede decidir con una anotación, porque exige mirar el recurso.</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Usuarios",
     description = """
             Administración de las cuentas de acceso. Cada usuario tiene un rol y, salvo el \
             administrador general, una sucursal: ese par determina sobre qué puede operar \
             (RN-12, RN-13). Es además el sujeto de la trazabilidad, ya que todo movimiento \
             de inventario registra quién lo provocó (RN-11).""")
public class UserController {

    private final ManageUserUseCase manageUserUseCase;
    private final QueryUserUseCase queryUserUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final UserWebMapper mapper;

    // ----------------------------------------------------------------------------------
    // Alta
    // ----------------------------------------------------------------------------------

    @PostMapping(value = "/organizations/{organizationId}/users",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "createUser",
            summary = "Dar de alta un usuario",
            description = """
                    Registra una cuenta de acceso (HU-02, HU-03, RF-03).

                    **Coherencia entre rol y sucursal.** `BRANCH_MANAGER` e \
                    `INVENTORY_OPERATOR` exigen `branchId`, porque operan dentro de una \
                    sucursal concreta (RN-13). `ADMIN` debe omitirlo: su ámbito es toda la \
                    organización (RN-12), y asignarle una sucursal haría ambiguo su alcance.

                    La contraseña se cifra antes de almacenarse y nunca se guarda en claro \
                    (RNF-03).

                    Solo el administrador general puede crear usuarios.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o caducado.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "El rol no autoriza a crear usuarios, o la organización no es la suya.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La organización, la sucursal o el rol no existen.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El correo ya está registrado en esa organización.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "Rol y sucursal incoherentes, o la sucursal pertenece a otra organización.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> createUser(
            @Parameter(description = "Organización en la que se registra el usuario.", required = true)
            @PathVariable UUID organizationId,

            @Valid @RequestBody CreateUserRequest request) {

        // Sin esto, un administrador podría crear usuarios en otra organización cambiando el
        // identificador de la ruta. La anotación de rol no lo impide: solo mira el rol.
        currentUserProvider.requireBelongsToOrganization(organizationId, "crear usuarios");

        User user = manageUserUseCase.createUser(mapper.toCommand(organizationId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/users/{id}")
                .buildAndExpand(user.getId())
                .toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(user));
    }

    // ----------------------------------------------------------------------------------
    // Consulta
    // ----------------------------------------------------------------------------------

    @GetMapping("/organizations/{organizationId}/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(
            operationId = "searchUsers",
            summary = "Listar usuarios de la organización",
            description = """
                    Devuelve los usuarios de la organización, filtrados y paginados.

                    Accesible al administrador general y a los gerentes de sucursal, que \
                    necesitan saber quién opera en la suya. El operador de inventario no \
                    tiene motivo para consultar el directorio de cuentas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuarios.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros de paginación u ordenación inválidos.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o caducado.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza, o la organización no es la suya.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<UserResponse> searchUsers(
            @Parameter(description = "Organización cuyos usuarios se consultan.", required = true)
            @PathVariable UUID organizationId,

            @Parameter(description = "Filtra por sucursal asignada.")
            @RequestParam(required = false) UUID branchId,

            @Parameter(description = "Filtra por rol.",
                       schema = @Schema(allowableValues = {"ADMIN", "BRANCH_MANAGER", "INVENTORY_OPERATOR"}))
            @RequestParam(required = false) RoleCode role,

            @Parameter(description = "Búsqueda parcial e insensible a mayúsculas sobre nombre, apellido y correo.",
                       example = "torres")
            @RequestParam(required = false) String text,

            @Parameter(description = "Filtra por estado de la cuenta. Si se omite, devuelve activas e inactivas.")
            @RequestParam(required = false) Boolean active,

            @Parameter(description = "Índice de página, empezando en 0.", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @Parameter(description = "Elementos por página. El máximo es 100.", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size,

            @Parameter(description = "Campo de ordenación.",
                       schema = @Schema(allowableValues = {"firstName", "lastName", "email",
                                                           "active", "lastLoginAt", "createdAt"},
                                        defaultValue = "lastName"))
            @RequestParam(required = false) String sortBy,

            @Parameter(description = "Sentido de la ordenación.",
                       schema = @Schema(allowableValues = {"ASC", "DESC"}, defaultValue = "ASC"))
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar usuarios");

        UserSearchCriteria criteria =
                new UserSearchCriteria(organizationId, branchId, role, text, active);

        PageQuery pageQuery =
                PageQuery.of(page, size, sortBy, SortDirection.fromString(sortDirection));

        PageResult<User> result = queryUserUseCase.searchUsers(criteria, pageQuery);

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/users/{userId}")
    @Operation(
            operationId = "getUserById",
            summary = "Consultar un usuario",
            description = """
                    Devuelve el detalle de un usuario.

                    Cualquier usuario puede consultarse a sí mismo. Consultar a otro requiere \
                    ser administrador general o gerente de sucursal.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o caducado.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "No autorizado a consultar a ese usuario.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un usuario con ese identificador.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse getUserById(
            @Parameter(description = "Identificador del usuario.", required = true)
            @PathVariable UUID userId) {

        requireSelfOrManager(userId, "consultar este usuario");
        return mapper.toResponse(queryUserUseCase.getUserById(userId));
    }

    // ----------------------------------------------------------------------------------
    // Modificación
    // ----------------------------------------------------------------------------------

    @PutMapping(value = "/users/{userId}/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateUserProfile",
            summary = "Actualizar los datos personales",
            description = """
                    Modifica nombre y apellido.

                    Cada usuario puede editar su propio perfil; el administrador general \
                    puede editar el de cualquiera.

                    El correo, el rol, la sucursal y la contraseña no se cambian aquí: cada \
                    uno tiene consecuencias propias y su propia operación.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "No autorizado a editar a ese usuario.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un usuario con ese identificador.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse updateProfile(
            @Parameter(description = "Identificador del usuario.", required = true)
            @PathVariable UUID userId,

            @Valid @RequestBody UpdateUserProfileRequest request) {

        requireSelfOrAdmin(userId, "editar este usuario");
        return mapper.toResponse(manageUserUseCase.updateProfile(mapper.toCommand(userId, request)));
    }

    @PutMapping(value = "/users/{userId}/assignment", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "reassignUser",
            summary = "Cambiar el rol y la sucursal de un usuario",
            description = """
                    Reasigna rol y sucursal a la vez (HU-03, RF-04).

                    **Van juntos porque están acoplados**: promover a `ADMIN` libera la \
                    sucursal, y dejar de serlo obliga a asignar una. Permitirlos por \
                    separado dejaría estados intermedios inválidos, como un gerente sin \
                    sucursal, incapaz de operar.

                    Es la operación con más alcance del módulo: redefine sobre qué puede \
                    actuar el usuario.

                    **No se puede degradar al último administrador activo**: dejaría la \
                    organización sin nadie capaz de gestionar usuarios ni sucursales, y sin \
                    forma de recuperarse desde la propia aplicación.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario reasignado.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza a reasignar usuarios.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario, el rol o la sucursal no existen.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = """
                            Rol y sucursal incoherentes, la sucursal es de otra organización, \
                            o es el último administrador activo.""",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse reassign(
            @Parameter(description = "Identificador del usuario.", required = true)
            @PathVariable UUID userId,

            @Valid @RequestBody ReassignUserRequest request) {

        return mapper.toResponse(manageUserUseCase.reassign(mapper.toCommand(userId, request)));
    }

    @PostMapping(value = "/users/{userId}/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "changePassword",
            summary = "Cambiar la contraseña",
            description = """
                    Sustituye la contraseña previa verificación de la actual.

                    **Solo el propio usuario puede cambiar su contraseña**, ni siquiera el \
                    administrador: para restablecer una cuenta ajena existe un flujo distinto, \
                    que no consiste en fijar una clave que el administrador conocería.

                    Exigir la contraseña actual no es redundante con estar autenticado: \
                    protege frente a que un token robado, o una sesión dejada abierta en un \
                    equipo compartido, basten para apoderarse de la cuenta.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña actualizada."),
            @ApiResponse(responseCode = "400", description = "Campos con formato inválido.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "La contraseña actual no coincide.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Se intentó cambiar la contraseña de otro usuario.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> changePassword(
            @Parameter(description = "Identificador del usuario.", required = true)
            @PathVariable UUID userId,

            @Valid @RequestBody ChangePasswordRequest request) {

        AuthenticatedUser current = currentUserProvider.require();
        if (!current.userId().equals(userId)) {
            throw new OperationNotPermittedException("cambiar la contraseña",
                    "solo el propio usuario puede cambiar su contraseña");
        }

        manageUserUseCase.changePassword(mapper.toCommand(userId, request));
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------------------------
    // Estado de la cuenta
    // ----------------------------------------------------------------------------------

    @PostMapping("/users/{userId}/deactivation")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "deactivateUser",
            summary = "Dar de baja una cuenta",
            description = """
                    Deshabilita la cuenta. Deja de poder autenticarse de inmediato.

                    Es baja lógica, nunca borrado: el usuario aparece como responsable en el \
                    histórico de movimientos, y eliminarlo dejaría esos registros sin poder \
                    explicar quién los produjo (RN-11, RNF-12).

                    **No se puede dar de baja al último administrador activo.** Sin ninguno, \
                    nadie podría gestionar usuarios ni sucursales, y habría que intervenir la \
                    base de datos a mano para recuperar el sistema.

                    La operación es idempotente.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta deshabilitada.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza a dar de baja usuarios.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un usuario con ese identificador.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Es el último administrador activo.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse deactivateUser(
            @Parameter(description = "Identificador del usuario.", required = true)
            @PathVariable UUID userId) {

        return mapper.toResponse(manageUserUseCase.deactivateUser(userId));
    }

    @PostMapping("/users/{userId}/activation")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "activateUser",
            summary = "Reactivar una cuenta",
            description = "Vuelve a habilitar una cuenta dada de baja. Es idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta reactivada.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no autoriza a reactivar usuarios.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un usuario con ese identificador.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse activateUser(
            @Parameter(description = "Identificador del usuario.", required = true)
            @PathVariable UUID userId) {

        return mapper.toResponse(manageUserUseCase.activateUser(userId));
    }

    // ----------------------------------------------------------------------------------
    // Apoyo
    // ----------------------------------------------------------------------------------

    /** Permite la operación si el objetivo es uno mismo, o si el solicitante es administrador. */
    private void requireSelfOrAdmin(UUID targetUserId, String operation) {
        AuthenticatedUser current = currentUserProvider.require();

        if (!current.userId().equals(targetUserId) && !current.isAdmin()) {
            throw new OperationNotPermittedException(operation,
                    "solo el propio usuario o un administrador pueden realizarla");
        }
    }

    /** Permite la operación si el objetivo es uno mismo, o si el solicitante supervisa. */
    private void requireSelfOrManager(UUID targetUserId, String operation) {
        AuthenticatedUser current = currentUserProvider.require();

        boolean isSelf = current.userId().equals(targetUserId);
        boolean supervises = current.role() == RoleCode.ADMIN
                || current.role() == RoleCode.BRANCH_MANAGER;

        if (!isSelf && !supervises) {
            throw new OperationNotPermittedException(operation,
                    "su rol no le autoriza a consultar otras cuentas");
        }
    }
}
