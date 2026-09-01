package io.github.KevinMitsi.inventories.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Volumen de ventas confirmadas de una sucursal en un mes calendario (RF-42/RF-43, HU-38):
 * base para comparar el mes actual contra meses anteriores.
 *
 * <p>Proyección de solo lectura — no es un agregado del dominio, igual que
 * {@code RouteComplianceSummary} (decisión de diseño #7 de Fase 5).
 *
 * @param branchId    sucursal a la que pertenece el período
 * @param year        año calendario del período
 * @param month       mes calendario del período (1-12)
 * @param saleCount   ventas confirmadas en el período
 * @param totalAmount suma de los subtotales netos de esas ventas
 */
public record SalesSummary(UUID branchId, String branchName, int year, int month, long saleCount,
                            BigDecimal totalAmount) {
}
