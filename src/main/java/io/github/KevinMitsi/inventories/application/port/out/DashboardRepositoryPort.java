package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.BranchComparison;
import io.github.KevinMitsi.inventories.domain.model.ProductRotation;
import io.github.KevinMitsi.inventories.domain.model.SalesSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Puerto de solo lectura para las proyecciones del dashboard (decisión de diseño #7, Fase 5). */
public interface DashboardRepositoryPort {

    List<SalesSummary> getSalesSummary(UUID organizationId, UUID branchId, Instant from, Instant to);

    List<ProductRotation> getProductRotation(UUID organizationId, UUID branchId, Instant from, Instant to);

    List<BranchComparison> getBranchComparison(UUID organizationId);
}
