package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición de alta de una sucursal (HU-04).
 *
 * <p>Pertenece al adaptador web y no viaja más adentro: el controlador lo convierte en un
 * comando antes de llamar al caso de uso. Así las anotaciones de Jackson y de Jakarta
 * Validation se quedan en la frontera, y cambiar el contrato de la API no obliga a tocar
 * la capa de aplicación.
 *
 * <p>La validación de aquí es de formato: obligatoriedad, longitud y patrón, all lo que
 * puede juzgarse mirando un campo por separado. Lo que exige comparar campos entre sí o
 * consultar el estado ya guardado —que el código no esté repetido, que la organización
 * exista— se comprueba en el dominio y en el servicio, no aquí.
 *
 * <p>La organización no es un campo del cuerpo: viaja en la ruta, porque identifica la
 * colección sobre la que se está creando el recurso.
 */
@Schema(name = "CreateBranchRequest", description = "Datos necesarios para registrar una sucursal.")
public record CreateBranchRequest(

        @Schema(description = """
                Código de negocio de la sucursal, único dentro de la organización. \
                Se normaliza a mayúsculas. Es inmutable una vez creada, porque aparece \
                en documentos y referencias operativas.""",
                example = "BOG-01", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El código de la sucursal es obligatorio.")
        @Size(max = 30, message = "El código no puede superar {max} caracteres.")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "El código solo admite letras, números, punto, guion y guion bajo.")
        String code,

        @Schema(description = "Nombre comercial de la sucursal.",
                example = "Sucursal Chapinero", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El nombre de la sucursal es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar {max} caracteres.")
        String name,

        @Schema(description = "Dirección física.", example = "Calle 63 #11-24")
        @Size(max = 250, message = "La dirección no puede superar {max} caracteres.")
        String addressLine,

        @Schema(description = "Ciudad.", example = "Bogotá")
        @Size(max = 100, message = "La ciudad no puede superar {max} caracteres.")
        String city,

        @Schema(description = "Código de país ISO 3166-1 alfa-2.", example = "CO")
        @Pattern(regexp = "^[A-Za-z]{2}$",
                message = "El código de país debe tener exactamente 2 letras (ISO 3166-1 alfa-2).")
        String countryCode,

        @Schema(description = "Teléfono de contacto.", example = "+57 601 5551234")
        @Size(max = 30, message = "El teléfono no puede superar {max} caracteres.")
        String phone
) {
}
