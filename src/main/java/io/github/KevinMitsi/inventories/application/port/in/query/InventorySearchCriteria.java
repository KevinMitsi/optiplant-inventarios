package io.github.KevinMitsi.inventories.application.port.in.query;

import java.util.UUID;

/**
 * @param branchId     sucursal cuyo inventario se consulta (obligatorio: RN-02, el stock
 *                     siempre se lee por sucursal)
 * @param lowStockOnly si es {@code true}, solo devuelve saldos en o por debajo del mínimo
 *                     configurado (HU-40, aprovecha {@code ix_inventory_low_stock})
 */
public record InventorySearchCriteria(UUID branchId, Boolean lowStockOnly) {

    public static InventorySearchCriteria ofBranch(UUID branchId) {
        return new InventorySearchCriteria(branchId, null);
    }
}
