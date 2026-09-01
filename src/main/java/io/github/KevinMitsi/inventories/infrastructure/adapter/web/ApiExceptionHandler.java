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
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.PropertyBindingException;
import tools.jackson.databind.exc.ValueInstantiationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
        // Los errores globales (validaciones que cruzan varios campos) no son FieldError y se
        // perderían al quedarse solo con getFieldErrors().
        List<ApiErrorResponse.ValidationError> errors = Stream.concat(
                        exception.getBindingResult().getFieldErrors().stream()
                                .map(this::toValidationError),
                        exception.getBindingResult().getGlobalErrors().stream()
                                .map(this::toValidationError))
                .toList();

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", describe(errors),
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

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", describe(errors),
                request, newTraceId(), null, errors);
    }

    /**
     * Validación de método de Spring: se dispara cuando las restricciones están sobre los
     * parámetros del controlador y no sobre un objeto de petición.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException exception,
                                                                   HttpServletRequest request) {
        List<ApiErrorResponse.ValidationError> errors = exception.getParameterValidationResults()
                .stream()
                .flatMap(result -> result instanceof ParameterErrors parameterErrors
                        ? parameterErrors.getAllErrors().stream().map(this::toValidationError)
                        : result.getResolvableErrors().stream().map(resolvable ->
                        new ApiErrorResponse.ValidationError(
                                result.getMethodParameter().getParameterName(),
                                resolvable.getDefaultMessage(),
                                maskIfSensitive(result.getMethodParameter().getParameterName(),
                                        result.getArgument()))))
                .toList();

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", describe(errors),
                request, newTraceId(), null, errors);
    }

    /**
     * El cuerpo no es JSON válido, o un valor no encaja con el tipo esperado.
     *
     * <p>Aquí es donde más ciego se queda un formulario: sin desenvolver la causa de Jackson,
     * el cliente solo sabe que "algo" del cuerpo está mal, pero no qué campo ni por qué. Se
     * extrae la ruta del campo y el valor recibido, sin devolver nunca el mensaje original de
     * Jackson, que incluye nombres de clases internas.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                                 HttpServletRequest request) {
        String traceId = newTraceId();
        log.debug("[{}] Cuerpo de petición ilegible en {}", traceId, request.getRequestURI(), exception);

        Throwable cause = exception.getCause();

        if (cause instanceof PropertyBindingException binding) {
            return unknownProperty(binding, request, traceId);
        }
        if (cause instanceof MismatchedInputException mismatch) {
            return mismatchedField(mismatch, request, traceId);
        }
        if (cause instanceof ValueInstantiationException instantiation) {
            return rejectedByConstructor(instantiation, request, traceId);
        }
        if (cause instanceof StreamReadException syntax) {
            return malformedJson(syntax, request, traceId);
        }

        // Cuerpo ausente: Spring no llega siquiera a invocar a Jackson, así que no hay causa.
        String message = cause == null
                ? "El cuerpo de la solicitud está vacío o no llegó como JSON."
                : "El cuerpo de la solicitud no es un JSON válido o no coincide con el formato esperado.";

        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message, request, traceId, null, null);
    }

    /** Campo que el DTO no declara: suele ser una errata o un cliente desactualizado. */
    private ResponseEntity<ApiErrorResponse> unknownProperty(PropertyBindingException exception,
                                                             HttpServletRequest request,
                                                             String traceId) {
        String field = fieldPath(exception, exception.getPropertyName());
        String message = "El campo '%s' no existe en esta solicitud.".formatted(field);

        return build(HttpStatus.BAD_REQUEST, "UNKNOWN_FIELD", message, request, traceId, null,
                List.of(new ApiErrorResponse.ValidationError(field, message, null)));
    }

    /** El valor recibido no encaja con el tipo del campo: fecha mal formada, UUID inválido, etc. */
    private ResponseEntity<ApiErrorResponse> mismatchedField(MismatchedInputException exception,
                                                             HttpServletRequest request,
                                                             String traceId) {
        String field = fieldPath(exception, null);
        Class<?> targetType = exception.getTargetType();
        String expected = describeType(targetType);
        Object rejected = exception instanceof InvalidFormatException invalidFormat
                ? maskIfSensitive(field, invalidFormat.getValue())
                : null;

        String message = "El campo '%s' no tiene un valor válido: se esperaba %s.".formatted(field, expected);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("field", field);
        details.put("expectedType", expected);
        if (targetType != null && targetType.isEnum()) {
            details.put("allowedValues", Stream.of(targetType.getEnumConstants()).map(Object::toString).toList());
        }

        return build(HttpStatus.BAD_REQUEST, "INVALID_FIELD_FORMAT", message, request, traceId, details,
                List.of(new ApiErrorResponse.ValidationError(field, message, rejected)));
    }

    /**
     * El constructor compacto del record rechazó el valor: el motivo lo escribió el dominio,
     * así que es apto para el usuario, a diferencia del mensaje de Jackson que lo envuelve.
     */
    private ResponseEntity<ApiErrorResponse> rejectedByConstructor(ValueInstantiationException exception,
                                                                   HttpServletRequest request,
                                                                   String traceId) {
        String field = fieldPath(exception, null);
        Throwable rootCause = exception.getCause();
        String reason = rootCause == null || rootCause.getMessage() == null
                ? "El valor recibido no es aceptable."
                : rootCause.getMessage();

        String message = "El campo '%s' fue rechazado: %s".formatted(field, reason);

        return build(HttpStatus.BAD_REQUEST, "INVALID_FIELD_VALUE", message, request, traceId, null,
                List.of(new ApiErrorResponse.ValidationError(field, reason, null)));
    }

    /** JSON sintácticamente roto: la posición es lo único accionable, y no filtra nada. */
    private ResponseEntity<ApiErrorResponse> malformedJson(StreamReadException exception,
                                                           HttpServletRequest request,
                                                           String traceId) {
        TokenStreamLocation location = exception.getLocation();
        Map<String, Object> details = location == null || location == TokenStreamLocation.NA
                ? null
                : Map.of("line", location.getLineNr(), "column", location.getColumnNr());

        return build(HttpStatus.BAD_REQUEST, "MALFORMED_JSON",
                "El cuerpo de la solicitud no es un JSON sintácticamente válido.",
                request, traceId, details, null);
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

    /** Un error global no señala a un campo concreto, así que {@code field} queda nulo. */
    private ApiErrorResponse.ValidationError toValidationError(ObjectError objectError) {
        if (objectError instanceof FieldError fieldError) {
            return toValidationError(fieldError);
        }
        return new ApiErrorResponse.ValidationError(null, objectError.getDefaultMessage(), null);
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

    /**
     * Resume los fallos en el {@code message}, para el cliente que solo muestra esa línea
     * y no recorre {@code validationErrors}.
     */
    private String describe(List<ApiErrorResponse.ValidationError> errors) {
        if (errors.isEmpty()) {
            return "La solicitud no supera la validación de entrada.";
        }

        String detail = errors.stream()
                .map(error -> error.field() == null
                        ? error.message()
                        : "%s: %s".formatted(error.field(), error.message()))
                .reduce((first, second) -> first + " " + second)
                .orElse("");

        return "La solicitud contiene %d campo(s) con valores inválidos. %s".formatted(errors.size(), detail);
    }

    /**
     * Reconstruye la ruta del campo desde el camino que Jackson va apilando
     * ({@code items[0].quantity}). No se usa {@code getPathReference()} porque antepone el
     * nombre completo de la clase del DTO.
     */
    private String fieldPath(JacksonException exception, String fallback) {
        List<String> segments = new ArrayList<>();

        for (JacksonException.Reference reference : exception.getPath()) {
            if (reference.getIndex() >= 0) {
                int last = segments.size() - 1;
                if (last >= 0) {
                    segments.set(last, segments.get(last) + "[" + reference.getIndex() + "]");
                } else {
                    segments.add("[" + reference.getIndex() + "]");
                }
            } else if (reference.getPropertyName() != null) {
                segments.add(reference.getPropertyName());
            }
        }

        if (segments.isEmpty()) {
            return fallback == null ? "(cuerpo)" : fallback;
        }
        return String.join(".", segments);
    }

    /** Traduce el tipo Java a algo que un usuario de la API pueda corregir sin ver el código. */
    private String describeType(Class<?> type) {
        if (type == null) {
            return "otro tipo de valor";
        }
        if (type.isEnum()) {
            return "uno de los valores admitidos";
        }
        if (type == UUID.class) {
            return "un identificador UUID (por ejemplo 6555069f-3d2b-430d-89e4-739b76c7d024)";
        }
        if (type == Instant.class || type == OffsetDateTime.class) {
            return "una fecha y hora ISO-8601 en UTC (por ejemplo 2026-09-01T14:30:00Z)";
        }
        if (type == LocalDate.class) {
            return "una fecha ISO-8601 (por ejemplo 2026-09-01)";
        }
        if (type == BigDecimal.class) {
            return "un número decimal";
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "un número entero";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true o false";
        }
        if (type == String.class) {
            return "un texto";
        }
        if (List.class.isAssignableFrom(type)) {
            return "una lista";
        }
        return "un objeto con la forma esperada";
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
