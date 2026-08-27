package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/**
 * Instrucción de modificación de los datos personales de un usuario.
 *
 * <p>No incluye correo, rol, sucursal ni contraseña. Cada uno de ellos tiene consecuencias
 * propias —el correo es la credencial de acceso, el rol y la sucursal definen el alcance de
 * autorización, la contraseña exige cifrado— y por eso se cambian por sus propias
 * operaciones, no editando una ficha genérica.
 */
public record UpdateUserProfileCommand(UUID userId, String firstName, String lastName) {
}
