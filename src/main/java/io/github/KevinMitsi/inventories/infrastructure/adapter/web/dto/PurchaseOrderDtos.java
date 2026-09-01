package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP de órdenes de compra (EP-05). */
public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    @Schema(name = "CreatePurchaseOrderRequest")
    public record CreatePurchaseOrderRequest(

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "El proveedor es obligatorio.")
            UUID supplierId,

            @Schema(description = "Número de orden, único dentro de la sucursal.", example = "OC-2026-0001",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @jakarta.validation.constraints.NotBlank(message = "El número de orden es obligatorio.")
            @Size(max = 40, message = "No puede superar {max} caracteres.")
            String orderNumber,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La fecha de la orden es obligatoria.")
            LocalDate orderDate,

            @Schema(description = "Plazo de pago acordado, en días.", example = "30")
            @PositiveOrZero(message = "El plazo de pago no puede ser negativo.")
            Integer paymentTermDays,

            @Schema(description = "Notas u observaciones.")
            String notes,

            @Schema(description = "Líneas de la orden; al menos una.", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty(message = "La orden debe tener al menos una línea.")
            @Valid
            List<ItemRequest> items
    ) {
        @Schema(name = "PurchaseOrderItemRequest")
        public record ItemRequest(

                @NotNull(message = "El producto de la línea es obligatorio.")
                UUID productId,

                @NotNull(message = "La cantidad de la línea es obligatoria.")
                @Positive(message = "La cantidad debe ser mayor que cero.")
                BigDecimal quantity,

                @Schema(description = "Precio pactado por unidad de la presentación indicada.")
                @NotNull(message = "El precio unitario es obligatorio.")
                @DecimalMin(value = "0", message = "El precio unitario no puede ser negativo.")
                BigDecimal unitPrice,

                @Schema(description = "Descuento de la línea, entre 0 y 100.")
                @DecimalMin(value = "0", message = "El descuento no puede ser negativo.")
                @jakarta.validation.constraints.DecimalMax(value = "100", message = "El descuento no puede superar 100.")
                BigDecimal discountPercentage
        ) {
        }
    }

    @Schema(name = "ReceivePurchaseOrderItemRequest")
    public record ReceivePurchaseOrderItemRequest(

            @Schema(description = "Cantidad recibida ahora, en la unidad de la línea.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La cantidad recibida es obligatoria.")
            @Positive(message = "La cantidad recibida debe ser mayor que cero.")
            BigDecimal quantityReceived
    ) {
    }

    @Schema(name = "PurchaseOrderResponse")
    public record PurchaseOrderResponse(
            UUID id, UUID branchId, UUID supplierId, UUID createdBy, String status, String orderNumber,
            LocalDate orderDate, int paymentTermDays, String notes, List<ItemResponse> items,
            Instant createdAt, Instant updatedAt
    ) {
        @Schema(name = "PurchaseOrderItemResponse")
        public record ItemResponse(
                UUID id, UUID productId, BigDecimal quantity, BigDecimal receivedQuantity,
                BigDecimal unitPrice, BigDecimal discountPercentage
        ) {
        }
    }
}
