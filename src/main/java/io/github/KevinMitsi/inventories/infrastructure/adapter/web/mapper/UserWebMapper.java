package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.application.port.in.command.ChangePasswordCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateUserCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReassignUserCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateUserProfileCommand;
import io.github.KevinMitsi.inventories.application.port.in.result.AuthenticationResult;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.AuthenticationResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ChangePasswordRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CreateUserRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ReassignUserRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.UpdateUserProfileRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

/**
 * Traduce entre los DTO del contrato HTTP y los tipos de la capa de aplicación.
 *
 * <p>Nótese qué <b>no</b> aparece en {@link #toResponse}: el hash de la contraseña. Como
 * {@code UserResponse} es una clase distinta del agregado, para filtrarlo habría que
 * añadirlo aquí a propósito. Serializando el dominio directamente, bastaría con olvidar una
 * anotación de exclusión.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserWebMapper {

    /**
     * Dominio a representación pública.
     *
     * <p>El rol se aplana en dos campos: el código, sobre el que el cliente decide qué
     * mostrar, y el nombre legible, que es lo que se pinta en pantalla.
     */
    @Mapping(target = "role", source = "role.code")
    @Mapping(target = "roleName", source = "role.name")
    UserResponse toResponse(User user);

    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "branchId", source = "request.branchId")
    @Mapping(target = "role", source = "request.role")
    @Mapping(target = "firstName", source = "request.firstName")
    @Mapping(target = "lastName", source = "request.lastName")
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "rawPassword", source = "request.password")
    CreateUserCommand toCommand(UUID organizationId, CreateUserRequest request);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "firstName", source = "request.firstName")
    @Mapping(target = "lastName", source = "request.lastName")
    UpdateUserProfileCommand toCommand(UUID userId, UpdateUserProfileRequest request);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "role", source = "request.role")
    @Mapping(target = "branchId", source = "request.branchId")
    ReassignUserCommand toCommand(UUID userId, ReassignUserRequest request);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "currentPassword", source = "request.currentPassword")
    @Mapping(target = "newPassword", source = "request.newPassword")
    ChangePasswordCommand toCommand(UUID userId, ChangePasswordRequest request);

    /**
     * Resultado de autenticación a respuesta HTTP.
     *
     * <p>La vigencia se convierte a segundos porque es lo que espera un cliente HTTP; y se
     * informa para que renueve el token antes de que caduque, en lugar de descubrirlo al
     * recibir un 401 en mitad de una operación.
     */
    default AuthenticationResponse toResponse(AuthenticationResult result) {
        if (result == null) {
            return null;
        }
        return new AuthenticationResponse(
                result.accessToken(),
                result.refreshToken(),
                "Bearer",
                result.accessTokenTtl().toSeconds(),
                toResponse(result.user()));
    }
}
