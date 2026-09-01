package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contratos HTTP de incidencias de transferencia (ENTITIES.md §15.3, HU-33). */
public final class TransferIssueDtos {

    private TransferIssueDtos() {
    }

    @Schema(name = "ResolveTransferIssueRequest")
    public record ResolveTransferIssueRequest(

            @Schema(allowableValues = {"RESHIPMENT", "ADJUSTMENT", "CLAIM"},
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El tipo de resolución es obligatorio.")
            String resolutionType
    ) {
    }

    @Schema(name = "TransferIssueResponse")
    public record TransferIssueResponse(
            UUID id, UUID transferItemId, String issueType, String resolutionType, BigDecimal quantity,
            String description, UUID reportedBy, Instant reportedAt, UUID resolvedBy, Instant resolvedAt
    ) {
    }
}
