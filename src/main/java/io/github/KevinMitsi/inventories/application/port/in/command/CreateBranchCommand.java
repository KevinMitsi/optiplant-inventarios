package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/**
 * Instrucción de alta de una sucursal (HU-04).
 *
 * <p>Es el tipo que cruza la frontera hacia la capa de aplicación. No se reutiliza el DTO
 * de la petición HTTP: ese pertenece al adaptador web y arrastra anotaciones de Jackson y
 * de Jakarta Validation que no tienen nada que hacer dentro del caso de uso. Si mañana la
 * misma operación se dispara desde una tarea programada o un consumidor de mensajes, el
 * comando sirve igual.
 *
 * <p>Es un {@code record}: los datos de entrada de una operación no deben poder cambiar
 * a mitad de su ejecución.
 */
public record CreateBranchCommand(UUID organizationId,
                                  String code,
                                  String name,
                                  String addressLine,
                                  String city,
                                  String countryCode,
                                  String phone) {
}
