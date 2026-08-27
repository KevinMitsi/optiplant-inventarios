package io.github.KevinMitsi.inventories.application.port.in.command;

import io.github.KevinMitsi.inventories.domain.model.RoleCode;

import java.util.UUID;

/**
 * Instrucción de reasignación de rol y sucursal (HU-03, RF-04).
 *
 * <p>Ambos cambian a la vez porque están acoplados: promover a administrador general libera
 * la sucursal, y dejar de serlo obliga a asignar una. Permitirlos por separado dejaría
 * estados intermedios inválidos, como un gerente sin sucursal, incapaz de operar.
 *
 * <p>Es la operación con más alcance del módulo: redefine sobre qué puede actuar el usuario.
 *
 * @param branchId sucursal destino; nulo si el rol destino es {@code ADMIN}
 */
public record ReassignUserCommand(UUID userId, RoleCode role, UUID branchId) {
}
