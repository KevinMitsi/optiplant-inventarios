package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/**
 * Instrucción de modificación de los datos descriptivos de una sucursal.
 *
 * <p>No incluye ni el código ni la organización: ambos forman parte de la identidad de la
 * sucursal y son inmutables una vez creada. Dejarlos fuera del comando hace imposible
 * intentar cambiarlos, en lugar de aceptarlos y rechazarlos después.
 *
 * <p>Tampoco incluye el estado de alta: activar o desactivar es una decisión de negocio
 * con sus propias consecuencias, no una edición de campo, y tiene su propio caso de uso.
 */
public record UpdateBranchCommand(UUID branchId,
                                  String name,
                                  String addressLine,
                                  String city,
                                  String countryCode,
                                  String phone) {
}
