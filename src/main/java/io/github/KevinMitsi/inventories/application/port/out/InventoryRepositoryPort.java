package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.InventorySearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepositoryPort {

    /**
     * @throws io.github.KevinMitsi.inventories.application.exception.ConcurrentModificationConflictException
     *         si otra operación modificó el mismo saldo primero (bloqueo optimista, RNF-05)
     */
    Inventory save(Inventory inventory);

    Optional<Inventory> findById(UUID id);

    Optional<Inventory> findByBranchIdAndProductId(UUID branchId, UUID productId);

    PageResult<Inventory> search(InventorySearchCriteria criteria, PageQuery pageQuery);
}
