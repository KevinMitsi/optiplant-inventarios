package io.github.KevinMitsi.inventories.application.port.in.command;

import io.github.KevinMitsi.inventories.domain.model.RoleCode;

import java.util.UUID;

/**
 * Instrucción de alta de un usuario (HU-02, HU-03).
 *
 * <p>El rol viaja como {@link RoleCode} y no como el identificador de la fila del catálogo:
 * quien llama razona en términos de "este usuario es gerente", no de un UUID que tendría
 * que consultar primero. El servicio resuelve el identificador contra el catálogo.
 *
 * <p>{@code branchId} debe venir informado para gerente y operador, y nulo para el
 * administrador general, cuyo alcance es toda la organización. El agregado {@code User}
 * hace cumplir esa coherencia.
 *
 * @param rawPassword contraseña en claro. Es el único punto del sistema donde entra, y se
 *                    cifra antes de construir el usuario.
 */
public record CreateUserCommand(UUID organizationId,
                                UUID branchId,
                                RoleCode role,
                                String firstName,
                                String lastName,
                                String email,
                                String rawPassword) {

    /** Enmascara la contraseña: este texto puede acabar en un log o en una traza. */
    @Override
    public String toString() {
        return "CreateUserCommand[email=%s, role=%s, branchId=%s, rawPassword=***]"
                .formatted(email, role, branchId);
    }
}
