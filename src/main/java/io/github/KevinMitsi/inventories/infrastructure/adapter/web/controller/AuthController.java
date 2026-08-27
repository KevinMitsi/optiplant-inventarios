package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.AuthenticateUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.AuthenticationCommand;
import io.github.KevinMitsi.inventories.application.port.in.result.AuthenticationResult;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.AuthenticationResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.LoginRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.RefreshTokenRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.UserResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.UserWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada HTTP para el acceso al sistema (EP-01, HU-01, RF-01).
 *
 * <p>Es el único controlador con endpoints públicos, y por un motivo obvio: sin credenciales
 * no hay token, y sin token no se llega a ningún otro sitio.
 */
@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Autenticación",
     description = """
             Acceso al sistema y gestión de la sesión. El token obtenido aquí se envía en \
             la cabecera `Authorization` con el prefijo `Bearer` en todas las demás \
             peticiones.""")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final QueryUserUseCase queryUserUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final UserWebMapper mapper;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    // Endpoint público: anula el requisito de token declarado globalmente en OpenApiConfig.
    @SecurityRequirements
    @Operation(
            operationId = "login",
            summary = "Iniciar sesión",
            description = """
                    Verifica las credenciales y devuelve los tokens de sesión (HU-01, RF-01).

                    Responde con dos tokens de propósito distinto:

                    - **`accessToken`**: autoriza las operaciones. Vive poco (1 hora), para \
                    limitar el daño si llegara a filtrarse.
                    - **`refreshToken`**: **no autoriza nada**. Solo sirve para obtener un \
                    token de acceso nuevo sin volver a pedir credenciales.

                    Incluye además el usuario, con su rol y su sucursal, para que el cliente \
                    pueda pintar la interfaz sin encadenar una segunda petición.

                    > **Nota sobre los errores.** Un correo inexistente, una contraseña \
                    incorrecta y una cuenta dada de baja producen exactamente la misma \
                    respuesta. Distinguirlas convertiría este endpoint en un medio de \
                    averiguar qué direcciones están registradas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación correcta.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthenticationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Faltan campos o tienen formato inválido.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401",
                    description = "Credenciales inválidas o cuenta deshabilitada.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthenticationResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = authenticateUserUseCase.authenticate(
                new AuthenticationCommand(request.email(), request.password()));

        return mapper.toResponse(result);
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(
            operationId = "refreshSession",
            summary = "Renovar la sesión",
            description = """
                    Emite un token de acceso nuevo a partir de uno de renovación, sin volver \
                    a pedir credenciales.

                    El usuario se recarga desde la base en lugar de confiar en lo que afirma \
                    el token. Es lo que hace efectiva una baja o un cambio de rol: el token \
                    de renovación sigue siendo criptográficamente válido, pero deja de \
                    servir en cuanto se comprueba el estado real de la cuenta.

                    Presentar aquí un token de **acceso** en lugar de uno de renovación se \
                    rechaza con 401.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión renovada.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthenticationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Falta el token de renovación.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401",
                    description = """
                            El token es inválido, ha caducado, es de acceso en lugar de \
                            renovación, o la cuenta fue deshabilitada.""",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthenticationResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return mapper.toResponse(authenticateUserUseCase.refresh(request.refreshToken()));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "getCurrentUser",
            summary = "Consultar el usuario de la sesión actual",
            description = """
                    Devuelve el usuario correspondiente al token presentado.

                    Se recarga desde la base en lugar de reconstruirlo con los datos del \
                    token: si el rol o la sucursal cambiaron después de emitirse, esta \
                    respuesta refleja el estado real y no la fotografía del momento de \
                    autenticarse.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario de la sesión actual.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o caducado.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse getCurrentUser() {
        return mapper.toResponse(
                queryUserUseCase.getUserById(currentUserProvider.requireUserId()));
    }
}
