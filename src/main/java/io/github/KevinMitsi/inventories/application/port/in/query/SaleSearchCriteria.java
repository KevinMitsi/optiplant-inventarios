package io.github.KevinMitsi.inventories.application.port.in.query;

import io.github.KevinMitsi.inventories.domain.model.SaleStatus;

import java.time.Instant;
import java.util.UUID;

/** Sostiene el histórico de ventas (RF-30) y el dashboard mensual (RF-42, HU-38). */
public record SaleSearchCriteria(UUID branchId, SaleStatus status, Instant fromDate, Instant toDate) {

    public static SaleSearchCriteria ofBranch(UUID branchId) {
        return new SaleSearchCriteria(branchId, null, null, null);
    }
}
