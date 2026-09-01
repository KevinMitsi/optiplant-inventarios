package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP de ventas (EP-06). */
public final class SaleDtos {

    private SaleDtos() {
    }

    @Schema(name = "CreateSaleRequest")
    public record CreateSaleRequest(

            @Schema(description = "Lista de precios contra la que se resuelven las líneas sin precio manual "
                    + "(HU-25).")
            UUID priceListId,

            @Schema(example = "V-2026-0001", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El número de venta es obligatorio.")
            @Size(max = 40, message = "No puede superar {max} caracteres.")
            String saleNumber,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La fecha de la venta es obligatoria.")
            Instant saleDate,

            String notes,

            @Schema(description = "Líneas de la venta; al menos una.", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty(message = "La venta debe tener al menos una línea.")
            @Valid
            List<ItemRequest> items
    ) {
        @Schema(name = "SaleItemRequest")
        public record ItemRequest(

                @NotNull(message = "El producto de la línea es obligatorio.")
                UUID productId,

                @NotNull(message = "La presentación de la línea es obligatoria.")
                UUID productUnitId,

                @NotNull(message = "La cantidad de la línea es obligatoria.")
                @Positive(message = "La cantidad debe ser mayor que cero.")
                BigDecimal quantity,

                @Schema(description = "Precio unitario manual. Si se omite, se toma de la lista de precios "
                        + "de la venta (HU-25).")
                @DecimalMin(value = "0", message = "El precio unitario no puede ser negativo.")
                BigDecimal unitPrice,

                @Schema(description = "Descuento de la línea, entre 0 y 100.")
                @DecimalMin(value = "0", message = "El descuento no puede ser negativo.")
                @DecimalMax(value = "100", message = "El descuento no puede superar 100.")
                BigDecimal discountPercentage
        ) {
        }
    }

    @Schema(name = "SaleResponse")
    public record SaleResponse(
            UUID id, UUID branchId, UUID createdBy, UUID priceListId, String status, String saleNumber,
            Instant saleDate, String notes, List<ItemResponse> items, BigDecimal total, Instant createdAt
    ) {
        @Schema(name = "SaleItemResponse")
        public record ItemResponse(
                UUID id, UUID productId, UUID productUnitId, BigDecimal quantity, BigDecimal unitPrice,
                BigDecimal discountPercentage
        ) {
        }
    }
}
