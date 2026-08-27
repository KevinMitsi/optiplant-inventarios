package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.ChangePasswordCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateUserCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReassignUserCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateUserProfileCommand;
import io.github.KevinMitsi.inventories.domain.model.User;

import java.util.UUID;

/**
 * Administración de usuarios (HU-02, HU-03, RF-03, RF-04).
 *
 * <p>Agrupa las operaciones de escritura sobre la cuenta porque comparten un mismo
 * consumidor y un mismo permiso: solo el administrador general gestiona usuarios. Separarlas
 * en cuatro interfaces produciría cuatro dependencias que siempre viajarían juntas, sin
 * ganar nada a cambio.
 *
 * <p>La consulta sí va aparte, en {@link QueryUserUseCase}, porque su alcance de
 * autorización es distinto: un gerente puede listar los usuarios de su sucursal aunque no
 * pueda crearlos.
 */
public interface ManageUserUseCase {

    /**
     * Da de alta un usuario.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException
     *         si la organización, la sucursal o el rol no existen
     * @throws io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException
     *         si el correo ya está registrado en esa organización
     */
    User createUser(CreateUserCommand command);

    /** Modifica nombre y apellido. */
    User updateProfile(UpdateUserProfileCommand command);

    /**
     * Cambia rol y sucursal a la vez.
     *
     * @throws io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException
     *         si el cambio dejaría a la organización sin ningún administrador activo
     */
    User reassign(ReassignUserCommand command);

    /**
     * Cambia la contraseña previa verificación de la actual.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.InvalidCredentialsException
     *         si la contraseña actual no coincide
     */
    void changePassword(ChangePasswordCommand command);

    /**
     * Da de baja la cuenta. Es baja lógica: el usuario aparece en el histórico de
     * movimientos y eliminarlo dejaría esos registros sin responsable (RN-11).
     *
     * @throws io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException
     *         si es el último administrador activo de la organización
     */
    User deactivateUser(UUID userId);

    /** Reactiva una cuenta dada de baja. */
    User activateUser(UUID userId);
}
