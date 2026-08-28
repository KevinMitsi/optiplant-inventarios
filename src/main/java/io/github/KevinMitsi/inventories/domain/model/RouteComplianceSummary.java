package io.github.KevinMitsi.inventories.domain.model;

import java.util.UUID;

/**
 * Cumplimiento estimado vs. real de una ruta logística (HU-36/HU-37): agrega, sobre las
 * transferencias despachadas y recibidas que usaron la ruta, el tiempo real de tránsito
 * ({@code received_at - shipped_at}) frente al {@code estimated_duration_minutes} de la ruta.
 *
 * <p>Proyección de solo lectura — no es un agregado del dominio, igual que las proyecciones
 * previstas para el dashboard (decisión de diseño #7 de Fase 5).
 *
 * @param completedTransfers    transferencias de esta ruta con despacho y recepción registrados
 * @param averageActualMinutes  promedio real de tránsito en minutos; {@code null} si no hay datos
 * @param onTimeTransfers       cuántas de esas transferencias llegaron dentro del estimado
 * @param onTimeRate            {@code onTimeTransfers / completedTransfers}; {@code null} si no hay datos
 */
public record RouteComplianceSummary(UUID routeId, UUID originBranchId, UUID destinationBranchId,
                                      int estimatedDurationMinutes, long completedTransfers,
                                      Double averageActualMinutes, long onTimeTransfers, Double onTimeRate) {
}
