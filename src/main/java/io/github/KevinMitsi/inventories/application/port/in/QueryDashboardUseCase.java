package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.domain.model.BranchComparison;
import io.github.KevinMitsi.inventories.domain.model.ProductRotation;
import io.github.KevinMitsi.inventories.domain.model.SalesSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consultas de solo lectura del dashboard analítico (EP-09, RF-42..RF-47, HU-38..42). */
public interface QueryDashboardUseCase {

    /**
     * Volumen de ventas confirmadas por mes calendario entre {@code from} y {@code to}
     * (RF-42/RF-43, HU-38). {@code branchId} nulo agrega toda la organización.
     */
    List<SalesSummary> getSalesSummary(UUID organizationId, UUID branchId, Instant from, Instant to);

    /**
     * Cantidad vendida por producto entre {@code from} y {@code to}, ordenada de mayor a
     * menor demanda (RF-44, HU-39). {@code branchId} nulo agrega toda la organización.
     */
    List<ProductRotation> getProductRotation(UUID organizationId, UUID branchId, Instant from, Instant to);

    /** Indicadores comparables entre las sucursales de la organización (RF-47, HU-42). */
    List<BranchComparison> getBranchComparison(UUID organizationId);
}
