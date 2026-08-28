package io.github.KevinMitsi.inventories.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Indicadores de una sucursal para comparar el desempeño general de la organización
 * (RF-47, HU-42): ventas confirmadas de los últimos 30 días, valor del inventario a costo
 * promedio ponderado, y cuántos productos están en o por debajo de su stock mínimo.
 *
 * <p>Proyección de solo lectura, mismo criterio que {@code SalesSummary}.
 */
public record BranchComparison(UUID branchId, String branchName, long saleCount30d,
                                BigDecimal totalSalesAmount30d, BigDecimal inventoryValue, long lowStockCount) {
}
