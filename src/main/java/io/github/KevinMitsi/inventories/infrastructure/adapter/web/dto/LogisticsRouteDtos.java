package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contratos HTTP de rutas logísticas (EP-08, HU-36/37, ENTITIES.md §16.2). */
public final class LogisticsRouteDtos {

    private LogisticsRouteDtos() {
    }

    @Schema(name = "CreateLogisticsRouteRequest")
    public record CreateLogisticsRouteRequest(

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La sucursal de origen es obligatoria.")
            UUID originBranchId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La sucursal de destino es obligatoria.")
            UUID destinationBranchId,

            @Size(max = 150, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La duración estimada es obligatoria.")
            @Positive(message = "La duración estimada debe ser mayor que cero.")
            Integer estimatedDurationMinutes,

            @Schema(description = "Costo estimado del trayecto.")
            @PositiveOrZero(message = "El costo estimado no puede ser negativo.")
            BigDecimal estimatedCost,

            @Schema(description = "Prioridad relativa entre rutas: 0 (baja) a 3 (extrema).", defaultValue = "0")
            @Min(value = 0, message = "La prioridad debe estar entre 0 y 3.")
            @Max(value = 3, message = "La prioridad debe estar entre 0 y 3.")
            Short priority
    ) {
    }

    @Schema(name = "UpdateLogisticsRouteRequest")
    public record UpdateLogisticsRouteRequest(

            @Size(max = 150, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @NotNull(message = "La duración estimada es obligatoria.")
            @Positive(message = "La duración estimada debe ser mayor que cero.")
            Integer estimatedDurationMinutes,

            @PositiveOrZero(message = "El costo estimado no puede ser negativo.")
            BigDecimal estimatedCost,

            @Schema(description = "Prioridad relativa entre rutas: 0 (baja) a 3 (extrema).", defaultValue = "0")
            @Min(value = 0, message = "La prioridad debe estar entre 0 y 3.")
            @Max(value = 3, message = "La prioridad debe estar entre 0 y 3.")
            Short priority
    ) {
    }

    @Schema(name = "LogisticsRouteResponse")
    public record LogisticsRouteResponse(
            UUID id, UUID organizationId, UUID originBranchId, UUID destinationBranchId, String name,
            int estimatedDurationMinutes, BigDecimal estimatedCost, short priority, boolean active,
            Instant createdAt, Instant updatedAt
    ) {
    }

    @Schema(name = "RouteComplianceResponse")
    public record RouteComplianceResponse(
            UUID routeId, UUID originBranchId, UUID destinationBranchId, int estimatedDurationMinutes,
            long completedTransfers, Double averageActualMinutes, long onTimeTransfers, Double onTimeRate
    ) {
    }
}
