package io.github.KevinMitsi.inventories.application.port.in.query;

import io.github.KevinMitsi.inventories.domain.model.PurchaseOrderStatus;

import java.util.UUID;

/**
 * @param productId filtra órdenes que contengan al menos una línea de este producto —
 *                   sostiene el histórico "por proveedor y producto" (RF-22, HU-20)
 */
public record PurchaseOrderSearchCriteria(UUID branchId, UUID supplierId, UUID productId,
                                          PurchaseOrderStatus status) {

    public static PurchaseOrderSearchCriteria ofBranch(UUID branchId) {
        return new PurchaseOrderSearchCriteria(branchId, null, null, null);
    }
}
