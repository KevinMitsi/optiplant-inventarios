package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** Contratos HTTP del dashboard analítico (EP-09, RF-42..RF-47, HU-38..42). */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    @Schema(name = "SalesSummaryResponse")
    public record SalesSummaryResponse(
            UUID branchId, String branchName, int year, int month, long saleCount, BigDecimal totalAmount
    ) {
    }

    @Schema(name = "ProductRotationResponse")
    public record ProductRotationResponse(
            UUID productId, String productName, BigDecimal quantitySold, long saleCount
    ) {
    }

    @Schema(name = "BranchComparisonResponse")
    public record BranchComparisonResponse(
            UUID branchId, String branchName, long saleCount30d, BigDecimal totalSalesAmount30d,
            BigDecimal inventoryValue, long lowStockCount
    ) {
    }
}
