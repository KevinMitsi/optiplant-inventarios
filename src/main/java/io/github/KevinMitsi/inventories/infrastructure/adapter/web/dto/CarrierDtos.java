package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Contratos HTTP de transportistas (EP-08, ENTITIES.md §16.1). */
public final class CarrierDtos {

    private CarrierDtos() {
    }

    @Schema(name = "CreateCarrierRequest")
    public record CreateCarrierRequest(

            @Schema(description = "Código único dentro de la organización.", example = "TRANS-01",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El código del transportista es obligatorio.")
            @Size(max = 30, message = "El código no puede superar {max} caracteres.")
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El nombre del transportista es obligatorio.")
            @Size(max = 150, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Schema(description = "Teléfono de contacto.")
            @Size(max = 30, message = "No puede superar {max} caracteres.")
            String phone,

            @Schema(description = "Correo de contacto.")
            @Email(message = "El correo no tiene un formato válido.")
            @Size(max = 254, message = "No puede superar {max} caracteres.")
            String email
    ) {
    }

    @Schema(name = "UpdateCarrierRequest")
    public record UpdateCarrierRequest(

            @NotBlank(message = "El nombre del transportista es obligatorio.")
            @Size(max = 150, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Size(max = 30, message = "No puede superar {max} caracteres.")
            String phone,

            @Email(message = "El correo no tiene un formato válido.")
            @Size(max = 254, message = "No puede superar {max} caracteres.")
            String email
    ) {
    }

    @Schema(name = "CarrierResponse")
    public record CarrierResponse(
            UUID id, UUID organizationId, String code, String name, String phone, String email, boolean active,
            Instant createdAt, Instant updatedAt
    ) {
    }
}
