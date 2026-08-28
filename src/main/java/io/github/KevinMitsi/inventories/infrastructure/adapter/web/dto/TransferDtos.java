package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP de transferencias entre sucursales (EP-07). */
public final class TransferDtos {

    private TransferDtos() {
    }

    @Schema(name = "CreateTransferRequest")
    public record CreateTransferRequest(

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "La sucursal de destino es obligatoria.")
            UUID destinationBranchId,

            @Schema(example = "TR-2026-0001", requiredMode = Schema.RequiredMode.REQUIRED)
            @jakarta.validation.constraints.NotBlank(message = "El número de transferencia es obligatorio.")
            @Size(max = 40, message = "No puede superar {max} caracteres.")
            String transferNumber,

            @Schema(allowableValues = {"LOW", "NORMAL", "HIGH", "URGENT"}, defaultValue = "NORMAL")
            String priority,

            String notes,

            @Schema(description = "Líneas de la transferencia; al menos una.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty(message = "La transferencia debe tener al menos una línea.")
            @Valid
            List<ItemRequest> items
    ) {
        @Schema(name = "TransferItemRequest")
        public record ItemRequest(

                @NotNull(message = "El producto de la línea es obligatorio.")
                UUID productId,

                @NotNull(message = "La presentación de la línea es obligatoria.")
                UUID productUnitId,

                @NotNull(message = "La cantidad solicitada es obligatoria.")
                @Positive(message = "La cantidad debe ser mayor que cero.")
                BigDecimal quantity
        ) {
        }
    }

    @Schema(name = "ItemQuantityRequest")
    public record ItemQuantityRequest(

            @NotNull(message = "El identificador de la línea es obligatorio.")
            UUID itemId,

            @NotNull(message = "La cantidad es obligatoria.")
            @PositiveOrZero(message = "La cantidad no puede ser negativa.")
            BigDecimal quantity
    ) {
    }

    @Schema(name = "ApproveTransferRequest")
    public record ApproveTransferRequest(

            @Schema(description = "Cantidad aprobada por línea. Si una línea no aparece, se "
                    + "aprueba tal como fue solicitada (HU-29).")
            @Valid
            List<ItemQuantityRequest> approvedQuantities
    ) {
    }

    @Schema(name = "DispatchTransferRequest")
    public record DispatchTransferRequest(

            @Schema(description = "Cantidad despachada por línea. Si una línea no aparece, se "
                    + "despacha por la cantidad aprobada.")
            @Valid
            List<ItemQuantityRequest> shippedQuantities
    ) {
    }

    @Schema(name = "ReceiveTransferRequest")
    public record ReceiveTransferRequest(

            @Schema(description = "Cantidad recibida por línea (RN-09). Si una línea no aparece, "
                    + "se considera que no llegó nada de ella.")
            @Valid
            List<ItemQuantityRequest> receivedQuantities
    ) {
    }

    @Schema(name = "TransferResponse")
    public record TransferResponse(
            UUID id, String transferNumber, UUID originBranchId, UUID destinationBranchId, UUID requestedBy,
            UUID approvedBy, String status, String priority, Instant requestedAt, Instant approvedAt,
            Instant shippedAt, Instant receivedAt, String notes, List<ItemResponse> items, Instant createdAt,
            Instant updatedAt
    ) {
        @Schema(name = "TransferItemResponse")
        public record ItemResponse(
                UUID id, UUID productId, UUID productUnitId, BigDecimal requestedQuantity,
                BigDecimal approvedQuantity, BigDecimal shippedQuantity, BigDecimal receivedQuantity
        ) {
        }
    }
}
