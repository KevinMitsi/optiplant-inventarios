package io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Cuerpo único de respuesta para cualquier error de la API.
 *
 * <p>Que todos los fallos compartan una sola forma permite al cliente escribir un solo
 * manejador de errores. El campo {@code code} es la clave de la reacción automática:
 * a diferencia del estado HTTP, que agrupa causas muy distintas bajo el mismo número,
 * identifica exactamente qué ocurrió y es estable entre versiones.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Respuesta estándar de error de la API.")
public record ApiErrorResponse(

        @Schema(description = "Instante en que se generó el error, en UTC.",
                example = "2026-08-27T14:32:10.512Z")
        Instant timestamp,

        @Schema(description = "Código de estado HTTP.", example = "409")
        int status,

        @Schema(description = "Nombre del código de estado HTTP.", example = "Conflict")
        String error,

        @Schema(description = """
                Código estable de la causa concreta. Es el valor sobre el que el cliente \
                debe programar su reacción, no el estado HTTP.""",
                example = "INSUFFICIENT_STOCK")
        String code,

        @Schema(description = "Explicación legible, apta para mostrarse al usuario final.",
                example = "Stock insuficiente para el producto 'SKU-001': se solicitaron 15 y hay 8 disponibles.")
        String message,

        @Schema(description = "Ruta de la petición que falló.", example = "/api/v1/sales")
        String path,

        @Schema(description = """
                Identificador de correlación. Aparece también en el log del servidor, \
                de modo que un usuario puede reportarlo y el equipo localizar la traza exacta.""",
                example = "b7f3c2a1-9e44-4b21-8f0d-1c2e3a4b5c6d")
        String traceId,

        @Schema(description = """
                Contexto estructurado del fallo: identificadores implicados, regla de negocio \
                incumplida, cantidades en conflicto. Su contenido depende de `code`.""",
                example = """
                        {"rule":"RN-03","productSku":"SKU-001","requestedQuantity":"15","availableQuantity":"8"}""")
        Map<String, Object> details,

        @Schema(description = "Errores campo a campo. Solo presente cuando falla la validación de entrada.")
        List<ValidationError> validationErrors
) {

    @Schema(name = "ValidationError", description = "Fallo de validación en un campo concreto de la petición.")
    public record ValidationError(

            @Schema(description = "Campo que no supera la validación.", example = "quantity")
            String field,

            @Schema(description = "Motivo del rechazo.", example = "La cantidad debe ser mayor que cero.")
            String message,

            @Schema(description = """
                    Valor recibido. Se omite en campos sensibles como contraseñas o tokens.""",
                    example = "-3")
            Object rejectedValue
    ) {
    }
}
