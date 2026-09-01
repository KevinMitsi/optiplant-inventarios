package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import io.github.KevinMitsi.inventories.application.exception.InvalidTokenException;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de {@link SecurityErrorResponder}.
 *
 * <p>No requiere contexto de Spring: la clase depende solo de un {@code ObjectMapper}, así
 * que basta con {@code spring-test}'s mock request/response para comprobar el formato exacto
 * de la respuesta que compone cuando un filtro —no un controlador— es quien falla.
 */
@DisplayName("SecurityErrorResponder")
class SecurityErrorResponderTest {

    private SecurityErrorResponder responder;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        responder = new SecurityErrorResponder(new ObjectMapper());
        request = new MockHttpServletRequest("GET", "/api/v1/branches");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("escribe 401 con el código de la excepción de dominio")
    void writesErrorFromDomainException() throws Exception {
        responder.writeError(request, response,
                new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

        assertThat(response.getStatus()).isEqualTo(401);

        ApiErrorResponse body = new ObjectMapper()
                .readValue(response.getContentAsByteArray(), ApiErrorResponse.class);
        assertThat(body.code()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(body.status()).isEqualTo(401);
        assertThat(body.path()).isEqualTo("/api/v1/branches");
        assertThat(body.traceId()).isNotBlank();
    }

    @Test
    @DisplayName("writeUnauthorized responde 401 sin detallar la causa")
    void writesUnauthorized() throws Exception {
        responder.writeUnauthorized(request, response);

        assertThat(response.getStatus()).isEqualTo(401);
        ApiErrorResponse body = new ObjectMapper()
                .readValue(response.getContentAsByteArray(), ApiErrorResponse.class);
        assertThat(body.code()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("writeForbidden responde 403 sin revelar qué rol haría falta")
    void writesForbidden() throws Exception {
        responder.writeForbidden(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
        ApiErrorResponse body = new ObjectMapper()
                .readValue(response.getContentAsByteArray(), ApiErrorResponse.class);
        assertThat(body.code()).isEqualTo("ACCESS_DENIED");
        assertThat(body.message()).doesNotContainIgnoringCase("ADMIN");
    }

    @Test
    @DisplayName("no vuelve a escribir si la respuesta ya se envió")
    void doesNothingIfResponseAlreadyCommitted() throws Exception {
        response.setCommitted(true);

        responder.writeUnauthorized(request, response);

        // MockHttpServletResponse conserva el estado por defecto: nada se sobrescribió.
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
