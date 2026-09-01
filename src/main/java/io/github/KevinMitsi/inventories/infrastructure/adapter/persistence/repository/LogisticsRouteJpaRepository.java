package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.LogisticsRouteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LogisticsRouteJpaRepository extends JpaRepository<LogisticsRouteJpaEntity, UUID>,
                                                       JpaSpecificationExecutor<LogisticsRouteJpaEntity> {

    boolean existsByOriginBranchIdAndDestinationBranchId(UUID originBranchId, UUID destinationBranchId);

    /**
     * Cumplimiento estimado vs. real por ruta (HU-36/HU-37): agrega, sobre las transferencias
     * de la ruta que ya tienen despacho y recepción, el tiempo real de tránsito en minutos
     * frente al {@code estimated_duration_minutes} de la ruta.
     *
     * <p>Nativa (no JPQL) porque agrega sobre columnas de {@code transfer}, otra tabla/agregado
     * distinto de {@code LogisticsRouteJpaEntity} — el mismo motivo por el que no hay una
     * relación JPA {@code Transfer -> LogisticsRoute} (ver decisión de diseño de
     * {@code TransferIssueJpaEntity.transferItemId} en el cierre de Fase 4).
     */
    @Query(value = """
            SELECT
                r.id AS routeId,
                r.origin_branch_id AS originBranchId,
                r.destination_branch_id AS destinationBranchId,
                r.estimated_duration_minutes AS estimatedDurationMinutes,
                COUNT(t.id) AS completedTransfers,
                AVG(EXTRACT(EPOCH FROM (t.received_at - t.shipped_at)) / 60.0) AS averageActualMinutes,
                COUNT(t.id) FILTER (
                    WHERE EXTRACT(EPOCH FROM (t.received_at - t.shipped_at)) / 60.0
                          <= r.estimated_duration_minutes
                ) AS onTimeTransfers
            FROM logistics_route r
            LEFT JOIN transfer t
                ON t.route_id = r.id AND t.shipped_at IS NOT NULL AND t.received_at IS NOT NULL
            WHERE r.organization_id = :organizationId
            GROUP BY r.id, r.origin_branch_id, r.destination_branch_id, r.estimated_duration_minutes
            ORDER BY r.id
            """, nativeQuery = true)
    List<RouteComplianceRow> findComplianceByOrganizationId(@Param("organizationId") UUID organizationId);

    /** Proyección plana del resultado nativo; el adaptador la traduce a {@code RouteComplianceSummary}. */
    interface RouteComplianceRow {
        UUID getRouteId();

        UUID getOriginBranchId();

        UUID getDestinationBranchId();

        int getEstimatedDurationMinutes();

        long getCompletedTransfers();

        Double getAverageActualMinutes();

        long getOnTimeTransfers();
    }
}
