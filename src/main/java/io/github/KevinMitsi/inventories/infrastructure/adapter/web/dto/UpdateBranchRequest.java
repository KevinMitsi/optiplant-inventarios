package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición de modificación de una sucursal.
 *
 * <p>No admite ni el código ni la organización: forman parte de la identidad de la
 * sucursal. Dejarlos fuera del contrato hace que ni siquiera se puedan intentar cambiar,
 * en lugar de aceptarlos para rechazarlos después.
 *
 * <p>Tampoco admite el estado de alta. Activar o desactivar tiene consecuencias operativas
 * propias y se hace por sus propios recursos, no editando un campo booleano.
 */
@Schema(name = "UpdateBranchRequest",
        description = "Datos modificables de una sucursal. El código y la organización son inmutables.")
public record UpdateBranchRequest(

        @Schema(description = "Nombre comercial de la sucursal.",
                example = "Sucursal Chapinero Norte", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El nombre de la sucursal es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar {max} caracteres.")
        String name,

        @Schema(description = "Dirección física.", example = "Calle 72 #10-34")
        @Size(max = 250, message = "La dirección no puede superar {max} caracteres.")
        String addressLine,

        @Schema(description = "Ciudad.", example = "Bogotá")
        @Size(max = 100, message = "La ciudad no puede superar {max} caracteres.")
        String city,

        @Schema(description = "Código de país ISO 3166-1 alfa-2.", example = "CO")
        @Pattern(regexp = "^[A-Za-z]{2}$",
                message = "El código de país debe tener exactamente 2 letras (ISO 3166-1 alfa-2).")
        String countryCode,

        @Schema(description = "Teléfono de contacto.", example = "+57 601 5559876")
        @Size(max = 30, message = "El teléfono no puede superar {max} caracteres.")
        String phone
) {
}
