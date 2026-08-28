package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Contratos HTTP de proveedores (RF-17). */
public final class SupplierDtos {

    private SupplierDtos() {
    }

    @Schema(name = "CreateSupplierRequest")
    public record CreateSupplierRequest(

            @Schema(description = "Código único dentro de la organización.", example = "PROV-01",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El código del proveedor es obligatorio.")
            @Size(max = 30, message = "El código no puede superar {max} caracteres.")
            String code,

            @Schema(description = "Razón social.", example = "Distribuidora Andina S.A.S.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "El nombre del proveedor es obligatorio.")
            @Size(max = 180, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Schema(description = "NIT o identificación tributaria.")
            @Size(max = 50, message = "No puede superar {max} caracteres.")
            String taxId,

            @Schema(description = "Correo de contacto.")
            @Email(message = "El correo no tiene un formato válido.")
            @Size(max = 254, message = "No puede superar {max} caracteres.")
            String email,

            @Schema(description = "Teléfono de contacto.")
            @Size(max = 30, message = "No puede superar {max} caracteres.")
            String phone
    ) {
    }

    @Schema(name = "UpdateSupplierRequest")
    public record UpdateSupplierRequest(

            @NotBlank(message = "El nombre del proveedor es obligatorio.")
            @Size(max = 180, message = "El nombre no puede superar {max} caracteres.")
            String name,

            @Size(max = 50, message = "No puede superar {max} caracteres.")
            String taxId,

            @Email(message = "El correo no tiene un formato válido.")
            @Size(max = 254, message = "No puede superar {max} caracteres.")
            String email,

            @Size(max = 30, message = "No puede superar {max} caracteres.")
            String phone
    ) {
    }

    @Schema(name = "SupplierResponse")
    public record SupplierResponse(
            UUID id, UUID organizationId, String code, String name, String taxId, String email, String phone,
            boolean active, Instant createdAt, Instant updatedAt
    ) {
    }
}
