package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Representación de una sucursal en las respuestas de la API.
 *
 * <p>Es una clase distinta del modelo de dominio a propósito. Devolver el agregado
 * directamente ataría el contrato público a la forma interna del dominio: cualquier campo
 * que se añadiera para uso interno se filtraría a los clientes, y cualquier renombrado
 * rompería a quien consume la API. Con un tipo propio, ambas cosas evolucionan por separado.
 */
@Schema(name = "BranchResponse", description = "Sucursal de la organización.")
public record BranchResponse(

        @Schema(description = "Identificador único de la sucursal.",
                example = "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID id,

        @Schema(description = "Organización a la que pertenece.",
                example = "9a8b7c6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d")
        UUID organizationId,

        @Schema(description = "Código de negocio, único dentro de la organización.", example = "BOG-01")
        String code,

        @Schema(description = "Nombre comercial.", example = "Sucursal Chapinero")
        String name,

        @Schema(description = "Dirección física.", example = "Calle 63 #11-24")
        String addressLine,

        @Schema(description = "Ciudad.", example = "Bogotá")
        String city,

        @Schema(description = "Código de país ISO 3166-1 alfa-2.", example = "CO")
        String countryCode,

        @Schema(description = "Teléfono de contacto.", example = "+57 601 5551234")
        String phone,

        @Schema(description = """
                Indica si la sucursal admite operaciones. Una sucursal inactiva conserva \
                todo su histórico y puede consultarse, pero no registra movimientos nuevos.""",
                example = "true")
        boolean active,

        @Schema(description = "Fecha de creación, en UTC.", example = "2026-01-15T09:30:00Z")
        Instant createdAt,

        @Schema(description = "Fecha de la última modificación, en UTC.", example = "2026-08-27T14:05:22Z")
        Instant updatedAt
) {
}
