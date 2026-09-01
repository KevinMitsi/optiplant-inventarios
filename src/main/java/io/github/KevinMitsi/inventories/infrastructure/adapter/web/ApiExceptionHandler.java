package io.github.KevinMitsi.inventories.infrastructure.adapter.web;

import io.github.KevinMitsi.inventories.domain.exception.DomainErrorCode;
import io.github.KevinMitsi.inventories.domain.exception.DomainException;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduce cualquier excepción que escape de un controlador a {@link ApiErrorResponse}.
 *
 * <p>Es el único punto que conoce a la vez el vocabulario del dominio y el de HTTP, lo que
 * permite que los servicios lancen excepciones de negocio sin saber que existe un 409.
 *
 * <p>Nunca filtra detalles internos ni el valor rechazado de un campo sensible (RNF-03).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Coincidencia parcial e insensible a mayúsculas, para cubrir {@code currentPassword} y similares. */
    private static final Set<String> SENSITIVE_FIELDS =
            Set.of("password", "passwordhash", "token", "secret", "credential", "authorization");

    private static final String GENERIC_ERROR_MESSAGE =
            "Se produjo un error inesperado al procesar la solicitud. "
                    + "Si el problema persiste, reporte el identificador de seguimiento.";

    // -----------------------------------------------------------------------------------
    // Errores de dominio y de aplicación
    // -----------------------------------------------------------------------------------

    /**
     * El estado HTTP se deduce del {@link DomainErrorCode} y no del tipo concreto, así que
     * una excepción nueva que reutilice un código existente no obliga a tocar esta clase.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException exception,
                                                        HttpServletRequest request) {
        HttpStatus status = toHttpStatus(exception.getErrorCode());
        String traceId = newTraceId();

        // Un 5xx nunca es culpa del cliente: se registra como error y con traza completa.
        // Un 4xx es una respuesta esperada del sistema, así que basta un aviso sin traza.
        if (status.is5xxServerError()) {
            log.error("[{}] Fallo de dominio {} en {}: {}",
                    traceId, exception.getErrorCode(), request.getRequestURI(),
                    exception.getMessage(), exception);
        } else {
            log.warn("[{}] {} en {}: {}",
                    traceId, exception.getErrorCode(), request.getRequestURI(), exception.getMessage());
        }

        return build(status, exception.getErrorCode().name(), exception.getMessage(),
                request, traceId, exception.getDetails(), null);
    }

    // -----------------------------------------------------------------------------------
    // Validación de la petición
    // -----------------------------------------------------------------------------------

    /** Falla la validación de Jakarta sobre un {@code @RequestBody}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception,
                                                                HttpServletRequest request) {
        List<ApiErrorResponse.ValidationError> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationError)
                .toList();

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud contiene %d campo(s) con valores inválidos.".formatted(errors.size()),
                request, newTraceId(), null, errors);
    }

    /** Falla la validación sobre parámetros de consulta o variables de ruta. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(ConstraintViolationException exception,
                                                                     HttpServletRequest request) {
        List<ApiErrorResponse.ValidationError> errors = exception.getConstraintViolations()
                .stream()
                .map(this::toValidationError)
                .toList();

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud contiene parámetros inválidos.",
                request, newTraceId(), null, errors);
    }

    /** El cuerpo no es JSON válido, o un valor no encaja con el tipo esperado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                                 HttpServletRequest request) {
        // El mensaje original expone nombres de clases internas: se sustituye por uno neutro.
        log.debug("Cuerpo de petición ilegible en {}", request.getRequestURI(), exception);
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "El cuerpo de la solicitud no es un JSON válido o no coincide con el formato esperado.",
                request, newTraceId(), null, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                               HttpServletRequest request) {
        String expected = exception.getRequiredType() == null
                ? "el tipo esperado"
                : exception.getRequiredType().getSimpleName();

        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "El parámetro '%s' no tiene un valor compatible con %s."
                        .formatted(exception.getName(), expected),
                request, newTraceId(),
                Map.of("parameter", exception.getName(), "expectedType", expected),
                null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Falta el parámetro obligatorio '%s'.".formatted(exception.getParameterName()),
                request, newTraceId(),
                Map.of("parameter", exception.getParameterName()), null);
    }

    // -----------------------------------------------------------------------------------
    // Seguridad
    // -----------------------------------------------------------------------------------

    /**
     * Autenticado, pero sin permiso.
     *
     * <p>El motivo real no se detalla: decir qué rol haría falta ayuda tanto al usuario
     * legítimo como a quien esté sondeando la API.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception,
                                                               HttpServletRequest request) {
        String traceId = newTraceId();
        log.warn("[{}] Acceso denegado a {}: {}", traceId, request.getRequestURI(), exception.getMessage());

        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "No tiene permisos para realizar esta operación.",
                request, traceId, null, null);
    }

    /** Credenciales ausentes, inválidas o token expirado. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception,
                                                                 HttpServletRequest request) {
        String traceId = newTraceId();
        log.warn("[{}] Autenticación fallida en {}: {}",
                traceId, request.getRequestURI(), exception.getMessage());

        return build(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Credenciales inválidas o sesión expirada.",
                request, traceId, null, null);
    }

    // -----------------------------------------------------------------------------------
    // Persistencia
    // -----------------------------------------------------------------------------------

    /**
     * Red de seguridad frente a dos escrituras concurrentes que pasen la validación previa.
     * El mensaje del driver se registra pero nunca se devuelve: describe el esquema interno.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception,
                                                                HttpServletRequest request) {
        String traceId = newTraceId();
        log.warn("[{}] Violación de integridad en {}: {}",
                traceId, request.getRequestURI(), exception.getMostSpecificCause().getMessage());

        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "La operación entra en conflicto con datos existentes o incumple una restricción de integridad.",
                request, traceId, null, null);
    }

    /** Bloqueo optimista: otra transacción tocó el mismo registro primero (RNF-05). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(OptimisticLockingFailureException exception,
                                                                    HttpServletRequest request) {
        String traceId = newTraceId();
        log.warn("[{}] Conflicto de concurrencia en {}: {}",
                traceId, request.getRequestURI(), exception.getMessage());

        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "El registro fue modificado por otra operación simultánea. "
                        + "Vuelva a consultarlo y repita la acción.",
                request, traceId, Map.of("retryable", true), null);
    }

    // -----------------------------------------------------------------------------------
    // Ruta y último recurso
    // -----------------------------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException exception,
                                                             HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND",
                "No existe el recurso solicitado.", request, newTraceId(), null, null);
    }

    /**
     * Fallo no contemplado. Se registra íntegro en el servidor y al cliente solo le llega un
     * mensaje genérico: la traza puede contener rutas, consultas y datos de otros usuarios.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception,
                                                             HttpServletRequest request) {
        String traceId = newTraceId();
        log.error("[{}] Error no controlado en {} {}",
                traceId, request.getMethod(), request.getRequestURI(), exception);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                GENERIC_ERROR_MESSAGE, request, traceId, null, null);
    }

    // -----------------------------------------------------------------------------------
    // Apoyo
    // -----------------------------------------------------------------------------------

    private HttpStatus toHttpStatus(DomainErrorCode errorCode) {
        return switch (errorCode) {
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_RESOURCE, CONCURRENT_MODIFICATION -> HttpStatus.CONFLICT;
            case OPERATION_NOT_PERMITTED -> HttpStatus.FORBIDDEN;
            // 401 y no 403: no se ha podido establecer quién realiza la petición.
            case AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
            // 422 y no 400: la petición está bien formada, lo que falla es su coherencia.
            case BUSINESS_RULE_VIOLATION, INSUFFICIENT_STOCK,
                 INVALID_STATE_TRANSITION, VALIDATION_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private ApiErrorResponse.ValidationError toValidationError(FieldError fieldError) {
        return new ApiErrorResponse.ValidationError(
                fieldError.getField(),
                fieldError.getDefaultMessage(),
                maskIfSensitive(fieldError.getField(), fieldError.getRejectedValue()));
    }

    private ApiErrorResponse.ValidationError toValidationError(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() == null
                ? null
                : lastPathSegment(violation.getPropertyPath().toString());

        return new ApiErrorResponse.ValidationError(
                field, violation.getMessage(),
                maskIfSensitive(field, violation.getInvalidValue()));
    }

    /** De {@code crearSucursal.request.code} solo interesa {@code code}. */
    private String lastPathSegment(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }

    /** Devuelve el valor rechazado, salvo que el campo sea sensible: entonces lo omite. */
    private Object maskIfSensitive(String field, Object value) {
        if (field == null || value == null) {
            return null;
        }
        String normalized = field.toLowerCase(Locale.ROOT);
        boolean sensitive = SENSITIVE_FIELDS.stream().anyMatch(normalized::contains);
        return sensitive ? null : value;
    }

    private String newTraceId() {
        return UUID.randomUUID().toString();
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status,
                                                   String code,
                                                   String message,
                                                   HttpServletRequest request,
                                                   String traceId,
                                                   Map<String, Object> details,
                                                   List<ApiErrorResponse.ValidationError> validationErrors) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                traceId,
                details == null || details.isEmpty() ? null : details,
                validationErrors == null || validationErrors.isEmpty() ? null : validationErrors);

        return ResponseEntity.status(status).body(body);
    }
}
