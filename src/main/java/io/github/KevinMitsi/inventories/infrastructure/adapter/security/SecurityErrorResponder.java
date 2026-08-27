package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.KevinMitsi.inventories.domain.exception.DomainException;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Compone respuestas de error desde la cadena de filtros de seguridad.
 *
 * <p>Existe por una limitación concreta: {@code @RestControllerAdvice} solo intercepta lo
 * que ocurre dentro de un controlador, y los filtros de seguridad se ejecutan antes de que
 * haya ninguno. Sin esta clase, un token caducado produciría la página de error por omisión
 * del contenedor —HTML, con otra forma y otros campos— justo en el punto donde el cliente
 * más necesita entender qué pasó.
 *
 * <p>Reutiliza {@link ApiErrorResponse}, de modo que un fallo de autenticación tiene
 * exactamente el mismo formato que cualquier otro error de la API.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorResponder.class);

    private final ObjectMapper objectMapper;

    /** Escribe el error correspondiente a una excepción de dominio surgida en un filtro. */
    public void writeError(HttpServletRequest request,
                           HttpServletResponse response,
                           DomainException exception) throws IOException {

        write(request, response, HttpStatus.UNAUTHORIZED,
                exception.getErrorCode().name(), exception.getMessage());
    }

    /** Petición sin credenciales sobre un recurso que las exige. */
    public void writeUnauthorized(HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {

        write(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Esta operación requiere autenticación. "
                        + "Incluya un token válido en la cabecera Authorization.");
    }

    /**
     * Autenticado, pero sin permiso.
     *
     * <p>El mensaje no detalla qué rol haría falta: esa información orienta tanto al usuario
     * legítimo como a quien esté sondeando los límites de la API.
     */
    public void writeForbidden(HttpServletRequest request,
                               HttpServletResponse response) throws IOException {

        write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "No tiene permisos para realizar esta operación.");
    }

    private void write(HttpServletRequest request,
                       HttpServletResponse response,
                       HttpStatus status,
                       String code,
                       String message) throws IOException {

        // Si la respuesta ya se envió, escribir de nuevo produce una excepción que
        // enmascararía el error original y llenaría el log de ruido.
        if (response.isCommitted()) {
            log.warn("No se pudo escribir el error {}: la respuesta ya fue enviada", code);
            return;
        }

        String traceId = UUID.randomUUID().toString();
        log.warn("[{}] {} en {} {}", traceId, code, request.getMethod(), request.getRequestURI());

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                traceId,
                null,
                null);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
