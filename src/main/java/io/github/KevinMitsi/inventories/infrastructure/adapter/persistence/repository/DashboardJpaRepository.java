package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SaleJpaEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consultas agregadas del dashboard (EP-09, RF-42..RF-47). Todas nativas porque agregan sobre
 * {@code sale}/{@code sale_item}/{@code inventory}, tablas ajenas a cualquier entidad JPA
 * concreta — mismo motivo que {@code LogisticsRouteJpaRepository.findComplianceByOrganizationId}.
 *
 * <p>Marcador {@code Repository<SaleJpaEntity, UUID>} en lugar de {@code JpaRepository}: no hay
 * un agregado propio del dashboard, así que no se expone CRUD, solo los métodos declarados aquí.
 */
public interface DashboardJpaRepository extends Repository<SaleJpaEntity, UUID> {

    /** Volumen de ventas confirmadas por mes calendario (RF-42/RF-43, HU-38). */
    @Query(value = """
            SELECT
                b.id AS branchId,
                b.name AS branchName,
                EXTRACT(YEAR FROM s.sale_date)::int AS year,
                EXTRACT(MONTH FROM s.sale_date)::int AS month,
                COUNT(DISTINCT s.id) AS saleCount,
                COALESCE(SUM(si.quantity * si.unit_price * (1 - si.discount_percentage / 100)), 0) AS totalAmount
            FROM sale s
            JOIN branch b ON b.id = s.branch_id
            JOIN sale_item si ON si.sale_id = s.id
            WHERE b.organization_id = :organizationId
              AND (:branchId IS NULL OR s.branch_id = :branchId)
              AND s.status = 'CONFIRMED'
              AND s.sale_date >= :from AND s.sale_date < :to
            GROUP BY b.id, b.name, year, month
            ORDER BY year, month, b.name
            """, nativeQuery = true)
    List<SalesSummaryRow> getSalesSummary(@Param("organizationId") UUID organizationId,
                                           @Param("branchId") UUID branchId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    /**
     * Cantidad vendida por producto en el período, de mayor a menor demanda (RF-44, HU-39).
     * {@code LEFT JOIN} contra la CTE para que los productos sin ventas en el período (baja
     * rotación) también aparezcan, con {@code quantitySold = 0}.
     */
    @Query(value = """
            WITH period_item AS (
                SELECT si.product_id, si.quantity, s.id AS sale_id
                FROM sale_item si
                JOIN sale s ON s.id = si.sale_id
                WHERE s.status = 'CONFIRMED'
                  AND s.sale_date >= :from AND s.sale_date < :to
                  AND (:branchId IS NULL OR s.branch_id = :branchId)
            )
            SELECT
                p.id AS productId,
                p.name AS productName,
                COALESCE(SUM(pi.quantity), 0) AS quantitySold,
                COUNT(DISTINCT pi.sale_id) AS saleCount
            FROM product p
            LEFT JOIN period_item pi ON pi.product_id = p.id
            WHERE p.organization_id = :organizationId AND p.active = TRUE
            GROUP BY p.id, p.name
            ORDER BY quantitySold DESC, p.name
            """, nativeQuery = true)
    List<ProductRotationRow> getProductRotation(@Param("organizationId") UUID organizationId,
                                                 @Param("branchId") UUID branchId,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to);

    /**
     * Indicadores por sucursal para comparar el desempeño de la organización (RF-47, HU-42):
     * ventas confirmadas de los últimos 30 días, valor de inventario a costo promedio
     * ponderado, y productos en o por debajo de su stock mínimo (reutiliza el criterio del
     * índice parcial {@code ix_inventory_low_stock}).
     */
    @Query(value = """
            SELECT
                b.id AS branchId,
                b.name AS branchName,
                COALESCE(sales.saleCount, 0) AS saleCount30d,
                COALESCE(sales.totalAmount, 0) AS totalSalesAmount30d,
                COALESCE(inv.inventoryValue, 0) AS inventoryValue,
                COALESCE(inv.lowStockCount, 0) AS lowStockCount
            FROM branch b
            LEFT JOIN (
                SELECT s.branch_id,
                       COUNT(DISTINCT s.id) AS saleCount,
                       SUM(si.quantity * si.unit_price * (1 - si.discount_percentage / 100)) AS totalAmount
                FROM sale s
                JOIN sale_item si ON si.sale_id = s.id
                WHERE s.status = 'CONFIRMED' AND s.sale_date >= now() - INTERVAL '30 days'
                GROUP BY s.branch_id
            ) sales ON sales.branch_id = b.id
            LEFT JOIN (
                SELECT i.branch_id,
                       SUM(i.quantity * i.average_cost) AS inventoryValue,
                       COUNT(*) FILTER (WHERE i.quantity <= i.minimum_stock) AS lowStockCount
                FROM inventory i
                GROUP BY i.branch_id
            ) inv ON inv.branch_id = b.id
            WHERE b.organization_id = :organizationId
            ORDER BY b.name
            """, nativeQuery = true)
    List<BranchComparisonRow> getBranchComparison(@Param("organizationId") UUID organizationId);

    /** Proyección plana; el adaptador la traduce a {@code SalesSummary}. */
    interface SalesSummaryRow {
        UUID getBranchId();

        String getBranchName();

        int getYear();

        int getMonth();

        long getSaleCount();

        BigDecimal getTotalAmount();
    }

    /** Proyección plana; el adaptador la traduce a {@code ProductRotation}. */
    interface ProductRotationRow {
        UUID getProductId();

        String getProductName();

        BigDecimal getQuantitySold();

        long getSaleCount();
    }

    /** Proyección plana; el adaptador la traduce a {@code BranchComparison}. */
    interface BranchComparisonRow {
        UUID getBranchId();

        String getBranchName();

        long getSaleCount30d();

        BigDecimal getTotalSalesAmount30d();

        BigDecimal getInventoryValue();

        long getLowStockCount();
    }
}
