package io.github.KevinMitsi.inventories.application.port.in.result;

import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;

import java.util.UUID;

/**
 * Una alerta junto a la sucursal y el producto del saldo que la disparó.
 *
 * <p>{@link InventoryAlert} solo guarda {@code inventoryId} (ENTITIES.md §17.2, normalizado):
 * no hay endpoint de consulta de un saldo por id, así que el frontend no tiene forma de
 * resolver esos dos datos por su cuenta. Esto envuelve la alerta con lo que ya resolvió
 * {@code InventoryAlertUseCase} contra el saldo, sin denormalizar el propio agregado.
 */
public record InventoryAlertDetail(InventoryAlert alert, UUID branchId, UUID productId) {
}
