package io.github.KevinMitsi.inventories.infrastructure.adapter.web;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.InvalidCredentialsException;
import io.github.KevinMitsi.inventories.application.exception.OperationNotPermittedException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.InsufficientStockException;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.SaleDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiExceptionHandler")
class ApiExceptionHandlerTest {

    private ApiExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/v1/sales");
    }

    @Nested
    @DisplayName("Correspondencia entre dominio y HTTP")
    class StatusMapping {

        @Test
        @DisplayName("un recurso inexistente se traduce a 404")
        void notFoundMapsTo404() {
            // Arrange
            ResourceNotFoundException exception = new ResourceNotFoundException("el producto", UUID.randomUUID());

            // Act
            ResponseEntity<ApiErrorResponse> response = handler.handleDomain(exception, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        }

        @Test
        @DisplayName("un duplicado se traduce a 409")
        void duplicateMapsTo409() {
            // Arrange
            DuplicateResourceException exception =
                    new DuplicateResourceException("el producto", "SKU", "BEB-001");

            // Act
            ResponseEntity<ApiErrorResponse> response = handler.handleDomain(exception, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("DUPLICATE_RESOURCE");
        }

        @Test
        @DisplayName("una regla de negocio incumplida se traduce a 422")
        void businessRuleMapsTo422() {
            // Arrange
            BusinessRuleViolationException exception =
                    new BusinessRuleViolationException("RN-07", "Origen y destino deben diferir.");

            // Act
            ResponseEntity<ApiErrorResponse> response = handler.handleDomain(exception, request);

            // Assert: la petición está bien formada; lo que falla es su coherencia
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody().details()).containsEntry("rule", "RN-07");
        }

        @Test
        @DisplayName("la falta de autorización se traduce a 403")
        void notPermittedMapsTo403() {
            // Arrange
            OperationNotPermittedException exception =
                    new OperationNotPermittedException("registrar la venta", "sucursal ajena");

            // Act
            ResponseEntity<ApiErrorResponse> response = handler.handleDomain(exception, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("un fallo de autenticación se traduce a 401")
        void authenticationFailureMapsTo401() {
            // Arrange
            InvalidCredentialsException exception = new InvalidCredentialsException();

            // Act
            ResponseEntity<ApiErrorResponse> response = handler.handleDomain(exception, request);

            // Assert: no sabemos quién pregunta, a diferencia del 403
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("el stock insuficiente lleva las cifras para que el cliente reaccione")
        void insufficientStockCarriesQuantities() {
            // Arrange
            InsufficientStockException exception = new InsufficientStockException(
                    UUID.randomUUID(), UUID.randomUUID(), "SKU-001",
                    new BigDecimal("15"), new BigDecimal("8"));

            // Act
            ResponseEntity<ApiErrorResponse> response = handler.handleDomain(exception, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody().details())
                    .containsEntry("requestedQuantity", "15")
                    .containsEntry("availableQuantity", "8")
                    .containsEntry("rule", "RN-03");
        }
    }

    @Nested
    @DisplayName("Contenido de la respuesta")
    class ResponseBody {

        @Test
        @DisplayName("incluye ruta, instante e identificador de correlación")
        void includesContext() {
            // Arrange
            ResourceNotFoundException exception = new ResourceNotFoundException("el producto", "id");

            // Act
            ApiErrorResponse body = handler.handleDomain(exception, request).getBody();

            // Assert
            assertThat(body.path()).isEqualTo("/api/v1/sales");
            assertThat(body.timestamp()).isNotNull();
            assertThat(body.traceId()).isNotBlank();
        }

        @Test
        @DisplayName("un fallo no controlado no filtra detalles internos")
        void unexpectedErrorHidesInternals() {
            // Arrange
            Exception exception = new IllegalStateException(
                    "Connection refused to jdbc:postgresql://internal-db:5432/prod");

            // Act
            ApiErrorResponse body = handler.handleUnexpected(exception, request).getBody();

            // Assert
            assertThat(body.message())
                    .as("la traza puede contener rutas, consultas y datos de otros usuarios")
                    .doesNotContain("jdbc", "internal-db", "Connection refused");
            assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
            assertThat(body.traceId()).isNotBlank();
        }

        @Test
        @DisplayName("un choque de integridad no expone el mensaje del driver")
        void dataIntegrityHidesDriverMessage() {
            // Arrange
            DataIntegrityViolationException exception = new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"uq_product_org_sku\"");

            // Act
            ApiErrorResponse body = handler.handleDataIntegrity(exception, request).getBody();

            // Assert: describe el esquema interno y no debe salir
            assertThat(body.message()).doesNotContain("uq_product_org_sku", "unique constraint");
            assertThat(body.code()).isEqualTo("DATA_INTEGRITY_VIOLATION");
        }

        @Test
        @DisplayName("el conflicto de concurrencia indica que se puede reintentar")
        void concurrencyConflictIsMarkedRetryable() {
            // Arrange
            OptimisticLockingFailureException exception =
                    new OptimisticLockingFailureException("Row was updated by another transaction");

            // Act
            ResponseEntity<ApiErrorResponse> response =
                    handler.handleOptimisticLocking(exception, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().details()).containsEntry("retryable", true);
        }

        @Test
        @DisplayName("el acceso denegado no detalla qué permiso haría falta")
        void accessDeniedStaysVague() {
            // Arrange
            AccessDeniedException exception = new AccessDeniedException("Requires ROLE_ADMIN");

            // Act
            ApiErrorResponse body = handler.handleAccessDenied(exception, request).getBody();

            // Assert: detallarlo orienta también a quien sondea la API
            assertThat(body.message()).doesNotContain("ROLE_ADMIN");
            assertThat(body.code()).isEqualTo("ACCESS_DENIED");
        }
    }

    @Nested
    @DisplayName("Validación de entrada")
    class Validation {

        private MethodArgumentNotValidException validationExceptionFor(String field,
                                                                      Object rejectedValue) {
            BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError("request", field, rejectedValue, false,
                    null, null, "El valor no es válido."));
            return new MethodArgumentNotValidException((MethodParameter) null, bindingResult);
        }

        @Test
        @DisplayName("devuelve 400 con el detalle campo a campo")
        void reportsFieldErrors() {
            // Arrange
            MethodArgumentNotValidException exception = validationExceptionFor("quantity", -3);

            // Act
            ResponseEntity<ApiErrorResponse> response =
                    handler.handleBodyValidation(exception, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().validationErrors()).hasSize(1);
            assertThat(response.getBody().validationErrors().getFirst().field()).isEqualTo("quantity");
            assertThat(response.getBody().validationErrors().getFirst().rejectedValue()).isEqualTo(-3);
        }

        @Test
        @DisplayName("nunca devuelve el valor rechazado de un campo sensible")
        void masksSensitiveRejectedValues() {
            // Arrange
            MethodArgumentNotValidException exception =
                    validationExceptionFor("password", "clave-en-claro-del-usuario");

            // Act
            ApiErrorResponse body = handler.handleBodyValidation(exception, request).getBody();

            // Assert
            assertThat(body.validationErrors().getFirst().rejectedValue())
                    .as("una contraseña inválida no puede acabar reflejada en la respuesta")
                    .isNull();
        }

        @Test
        @DisplayName("enmascara también las variantes del nombre de campo")
        void masksSensitiveFieldVariants() {
            // Arrange
            MethodArgumentNotValidException exception =
                    validationExceptionFor("currentPassword", "clave-actual");

            // Act
            ApiErrorResponse body = handler.handleBodyValidation(exception, request).getBody();

            // Assert
            assertThat(body.validationErrors().getFirst().rejectedValue()).isNull();
        }

        @Test
        @DisplayName("el mensaje resume los campos, para el cliente que solo muestra esa línea")
        void messageNamesTheOffendingFields() {
            // Arrange
            MethodArgumentNotValidException exception = validationExceptionFor("quantity", -3);

            // Act
            ApiErrorResponse body = handler.handleBodyValidation(exception, request).getBody();

            // Assert
            assertThat(body.message()).contains("quantity", "El valor no es válido.");
        }

        @Test
        @DisplayName("un error global, que no señala a un campo, no se pierde")
        void reportsGlobalErrors() {
            // Arrange
            BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new ObjectError("request", "La fecha final precede a la inicial."));
            MethodArgumentNotValidException exception =
                    new MethodArgumentNotValidException((MethodParameter) null, bindingResult);

            // Act
            ApiErrorResponse body = handler.handleBodyValidation(exception, request).getBody();

            // Assert
            assertThat(body.validationErrors()).hasSize(1);
            assertThat(body.validationErrors().getFirst().field()).isNull();
            assertThat(body.validationErrors().getFirst().message())
                    .isEqualTo("La fecha final precede a la inicial.");
        }
    }

    @Nested
    @DisplayName("Cuerpo ilegible")
    class UnreadableBody {

        private final ObjectMapper objectMapper = new ObjectMapper();

        /** Reproduce el fallo real de Jackson en vez de simularlo: el camino del campo importa. */
        private HttpMessageNotReadableException parseFailureFor(String json) {
            try {
                objectMapper.readValue(json, SaleDtos.CreateSaleRequest.class);
                throw new AssertionError("Se esperaba que el JSON fuese rechazado: " + json);
            } catch (JacksonException cause) {
                return new HttpMessageNotReadableException("no leído", cause,
                        new MockHttpInputMessage(json.getBytes(StandardCharsets.UTF_8)));
            }
        }

        @Test
        @DisplayName("una fecha mal formada dice qué campo y qué formato se esperaba")
        void reportsBadDateField() {
            // Arrange: lo que manda un <input type="datetime-local"> sin convertir a UTC
            HttpMessageNotReadableException exception = parseFailureFor("""
                    {"saleNumber":"V-1","saleDate":"2026-09-01T10:30","items":[]}""");

            // Act
            ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableBody(exception, request);
            ApiErrorResponse body = response.getBody();

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(body.code()).isEqualTo("INVALID_FIELD_FORMAT");
            assertThat(body.validationErrors()).singleElement()
                    .extracting(ApiErrorResponse.ValidationError::field).isEqualTo("saleDate");
            assertThat(body.message()).contains("ISO-8601");
        }

        @Test
        @DisplayName("un identificador inválido dentro de una línea señala su posición en la lista")
        void reportsPathInsideCollection() {
            // Arrange
            HttpMessageNotReadableException exception = parseFailureFor("""
                    {"saleNumber":"V-1","saleDate":"2026-09-01T10:30:00Z",\
                    "items":[{"productId":"no-es-un-uuid","quantity":1}]}""");

            // Act
            ApiErrorResponse body = handler.handleUnreadableBody(exception, request).getBody();

            // Assert
            assertThat(body.validationErrors()).singleElement()
                    .extracting(ApiErrorResponse.ValidationError::field).isEqualTo("items[0].productId");
            assertThat(body.validationErrors().getFirst().rejectedValue()).isEqualTo("no-es-un-uuid");
        }

        @Test
        @DisplayName("un JSON roto devuelve la posición del fallo y nada más")
        void reportsSyntaxPosition() {
            // Arrange
            HttpMessageNotReadableException exception = parseFailureFor("{\"saleNumber\":");

            // Act
            ApiErrorResponse body = handler.handleUnreadableBody(exception, request).getBody();

            // Assert
            assertThat(body.code()).isEqualTo("MALFORMED_JSON");
            assertThat(body.details()).containsKey("line").containsKey("column");
        }

        @Test
        @DisplayName("un cuerpo ausente se distingue de un cuerpo mal formado")
        void reportsMissingBody() {
            // Arrange: sin causa de Jackson, porque no se llegó a analizar nada
            HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                    "Required request body is missing", new MockHttpInputMessage(new byte[0]));

            // Act
            ApiErrorResponse body = handler.handleUnreadableBody(exception, request).getBody();

            // Assert
            assertThat(body.code()).isEqualTo("MALFORMED_REQUEST");
            assertThat(body.message()).contains("vacío");
        }

        @Test
        @DisplayName("nunca devuelve el mensaje original de Jackson, que nombra clases internas")
        void hidesJacksonInternals() {
            // Arrange
            HttpMessageNotReadableException exception = parseFailureFor("""
                    {"saleNumber":"V-1","saleDate":"2026-09-01T10:30","items":[]}""");

            // Act
            ApiErrorResponse body = handler.handleUnreadableBody(exception, request).getBody();

            // Assert
            assertThat(body.message()).doesNotContain("io.github", "CreateSaleRequest", "java.time");
        }
    }
}
