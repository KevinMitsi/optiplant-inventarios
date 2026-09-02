package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contratos HTTP de alertas de reabastecimiento (funcionalidad adicional §34). */
public final class InventoryAlertDtos {

    private InventoryAlertDtos() {
    }

    @Schema(name = "InventoryAlertResponse")
    public record InventoryAlertResponse(
            @Schema(description = "Identificador de la alerta.") UUID id,
            @Schema(description = "Saldo que la disparó.") UUID inventoryId,
            @Schema(description = "Sucursal del saldo que la disparó.") UUID branchId,
            @Schema(description = "Producto del saldo que la disparó.") UUID productId,
            @Schema(description = "Tipo de alerta.", example = "LOW_STOCK") String alertType,
            @Schema(description = "Estado.", example = "OPEN") String status,
            @Schema(description = "Cantidad disponible en el momento de dispararse.") BigDecimal triggeredQuantity,
            @Schema(description = "Mínimo configurado en ese momento.") BigDecimal minimumStock,
            @Schema(description = "Mensaje legible.") String message,
            @Schema(description = "Fecha en que se abrió, en UTC.") Instant createdAt,
            @Schema(description = "Fecha de cierre, en UTC; nula si sigue abierta.") Instant resolvedAt
    ) {
    }
}
