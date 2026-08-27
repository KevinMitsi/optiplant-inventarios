package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/**
 * Instrucción de cambio de contraseña.
 *
 * <p>Exige la contraseña actual además de la nueva. La comprobación no es redundante con
 * estar autenticado: protege frente a que un token robado, o una sesión dejada abierta,
 * baste para apoderarse de la cuenta cambiándole la clave.
 *
 * @param currentPassword contraseña vigente, que debe verificarse antes de aplicar el cambio
 * @param newPassword     contraseña nueva en claro, que se cifra antes de persistirse
 */
public record ChangePasswordCommand(UUID userId, String currentPassword, String newPassword) {

    /** Enmascara ambas contraseñas: este texto puede acabar en un log o en una traza. */
    @Override
    public String toString() {
        return "ChangePasswordCommand[userId=%s, currentPassword=***, newPassword=***]"
                .formatted(userId);
    }
}
