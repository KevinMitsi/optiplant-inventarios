package io.github.KevinMitsi.inventories.application.port.in.query;

import io.github.KevinMitsi.inventories.domain.model.InventoryAlertStatus;

import java.util.UUID;

/**
 * @param branchId filtra por sucursal; nulo para verlas todas (ADMIN, RN-12)
 * @param status   filtra por estado; nulo para verlas todas
 */
public record InventoryAlertSearchCriteria(UUID branchId, InventoryAlertStatus status) {

    public static InventoryAlertSearchCriteria openInBranch(UUID branchId) {
        return new InventoryAlertSearchCriteria(branchId, InventoryAlertStatus.OPEN);
    }
}
