package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.InventorySearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;

public interface QueryInventoryUseCase {

    Inventory getByBranchAndProduct(UUID branchId, UUID productId);

    PageResult<Inventory> searchInventory(InventorySearchCriteria criteria, PageQuery pageQuery);

    /** Histórico de movimientos de un saldo, del más reciente al más antiguo (HU-14). */
    PageResult<InventoryMovement> getMovementHistory(UUID branchId, UUID productId, PageQuery pageQuery);
}
