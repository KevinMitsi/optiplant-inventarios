package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP de ajustes de inventario formales (ENTITIES.md §18). */
public final class InventoryAdjustmentDtos {

    private InventoryAdjustmentDtos() {
    }

    @Schema(name = "CreateInventoryAdjustmentRequest", description = "Ajuste de inventario en borrador, con sus líneas.")
    public record CreateInventoryAdjustmentRequest(

            @Schema(description = "Motivo general del ajuste.", example = "Conteo físico de fin de mes",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El motivo del ajuste es obligatorio.")
            @Size(max = 250, message = "El motivo no puede superar {max} caracteres.")
            String reason,

            @Schema(description = "Líneas del ajuste; al menos una.", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty(message = "El ajuste debe tener al menos una línea.")
            List<@Valid ItemRequest> items
    ) {
        @Schema(name = "InventoryAdjustmentItemRequest")
        public record ItemRequest(

                @Schema(description = "Producto ajustado.", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull(message = "El producto de la línea es obligatorio.")
                UUID productId,

                @Schema(description = "Cantidad con signo: positivo entra, negativo sale.", example = "-3",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull(message = "La cantidad de la línea es obligatoria.")
                BigDecimal quantityDelta,

                @Schema(description = "Motivo específico de la línea, si difiere del general.")
                @Size(max = 250, message = "El motivo no puede superar {max} caracteres.")
                String reason
        ) {
        }
    }

    @Schema(name = "InventoryAdjustmentResponse")
    public record InventoryAdjustmentResponse(
            @Schema(description = "Identificador del ajuste.") UUID id,
            @Schema(description = "Sucursal.") UUID branchId,
            @Schema(description = "Quien creó el ajuste.") UUID createdBy,
            @Schema(description = "Quien lo aprobó; nulo mientras está en borrador.") UUID approvedBy,
            @Schema(description = "Motivo general.") String reason,
            @Schema(description = "Si ya fue aprobado y sus movimientos posteados.") boolean approved,
            @Schema(description = "Líneas del ajuste.") List<ItemResponse> items,
            @Schema(description = "Fecha de creación, en UTC.") Instant createdAt,
            @Schema(description = "Fecha de aprobación, en UTC; nula en borrador.") Instant approvedAt
    ) {
        @Schema(name = "InventoryAdjustmentItemResponse")
        public record ItemResponse(
                UUID id,
                UUID productId,
                BigDecimal quantityDelta,
                String reason
        ) {
        }
    }
}
