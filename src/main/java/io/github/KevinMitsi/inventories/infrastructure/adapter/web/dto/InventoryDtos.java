package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contratos HTTP del módulo de inventario y movimientos (EP-04). */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    @Schema(name = "SetMinimumStockRequest", description = "Stock mínimo de un producto en una sucursal.")
    public record SetMinimumStockRequest(

            @Schema(description = "Stock mínimo, en la unidad del producto.", example = "10",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El stock mínimo es obligatorio.")
            @DecimalMin(value = "0", message = "El stock mínimo no puede ser negativo.")
            BigDecimal minimumStock
    ) {
    }

    @Schema(name = "RegisterInventoryMovementRequest",
            description = "Entrada o salida manual de inventario, sin documento de origen (HU-12/HU-13).")
    public record RegisterInventoryMovementRequest(

            @Schema(description = "Producto sobre el que se registra el movimiento.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El producto es obligatorio.")
            UUID productId,

            @Schema(description = "Cantidad, en la unidad del producto.", example = "5",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La cantidad es obligatoria.")
            @Positive(message = "La cantidad debe ser mayor que cero.")
            BigDecimal quantity,

            @Schema(description = "Motivo del movimiento.", example = "Devolución de cliente",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El motivo es obligatorio (RN-11).")
            @Size(max = 250, message = "El motivo no puede superar {max} caracteres.")
            String reason
    ) {
    }

    @Schema(name = "InventoryResponse", description = "Saldo de un producto en una sucursal.")
    public record InventoryResponse(
            @Schema(description = "Identificador del saldo.") UUID id,
            @Schema(description = "Sucursal.") UUID branchId,
            @Schema(description = "Producto.") UUID productId,
            @Schema(description = "Cantidad disponible, en la unidad del producto.") BigDecimal quantity,
            @Schema(description = "Stock mínimo configurado.") BigDecimal minimumStock,
            @Schema(description = "Costo promedio ponderado (RF-23).") BigDecimal averageCost,
            @Schema(description = "Si el saldo está en o por debajo del mínimo.") boolean lowStock,
            @Schema(description = "Si el saldo está en cero.") boolean outOfStock,
            @Schema(description = "Fecha de la última actualización, en UTC.") Instant updatedAt
    ) {
    }

    @Schema(name = "InventoryMovementResponse", description = "Movimiento de inventario, del histórico auditable.")
    public record InventoryMovementResponse(
            @Schema(description = "Identificador del movimiento.") UUID id,
            @Schema(description = "Saldo al que pertenece.") UUID inventoryId,
            @Schema(description = "Tipo de movimiento.", example = "PURCHASE_IN") String movementType,
            @Schema(description = "Sentido del cambio de stock.", example = "IN") String direction,
            @Schema(description = "Responsable del movimiento (RN-11).") UUID userId,
            @Schema(description = "Cantidad, siempre positiva; el sentido lo aporta el tipo.") BigDecimal quantity,
            @Schema(description = "Costo unitario, solo presente en compras.") BigDecimal unitCost,
            @Schema(description = "Motivo del movimiento.") String reason,
            @Schema(description = "Orden de compra de origen, si aplica.") UUID purchaseOrderId,
            @Schema(description = "Venta de origen, si aplica.") UUID saleId,
            @Schema(description = "Transferencia de origen, si aplica.") UUID transferId,
            @Schema(description = "Ajuste de origen, si aplica.") UUID adjustmentId,
            @Schema(description = "Fecha en que ocurrió el movimiento, en UTC.") Instant occurredAt,
            @Schema(description = "Fecha de registro, en UTC.") Instant createdAt
    ) {
    }
}
