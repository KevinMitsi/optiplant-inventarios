package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contratos HTTP de listas de precios y precios por producto (EP-06, RF-29). */
public final class PriceListDtos {

    private PriceListDtos() {
    }

    @Schema(name = "CreatePriceListRequest")
    public record CreatePriceListRequest(

            @Schema(example = "MINORISTA", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El código es obligatorio.")
            @Size(max = 30, message = "No puede superar {max} caracteres.")
            String code,

            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 100, message = "No puede superar {max} caracteres.")
            String name,

            String description,

            LocalDate validFrom,

            LocalDate validUntil
    ) {
    }

    @Schema(name = "UpdatePriceListRequest")
    public record UpdatePriceListRequest(

            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 100, message = "No puede superar {max} caracteres.")
            String name,

            String description,

            LocalDate validFrom,

            LocalDate validUntil
    ) {
    }

    @Schema(name = "SetProductPriceRequest")
    public record SetProductPriceRequest(

            @NotNull(message = "El producto es obligatorio.")
            UUID productId,

            @NotNull(message = "El precio es obligatorio.")
            @DecimalMin(value = "0", message = "El precio no puede ser negativo.")
            BigDecimal price
    ) {
    }

    @Schema(name = "PriceListResponse")
    public record PriceListResponse(
            UUID id, UUID organizationId, String code, String name, String description, boolean active,
            LocalDate validFrom, LocalDate validUntil, Instant createdAt, Instant updatedAt
    ) {
    }

    @Schema(name = "ProductPriceResponse")
    public record ProductPriceResponse(
            UUID id, UUID priceListId, UUID productId, BigDecimal price
    ) {
    }
}
