package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import io.github.KevinMitsi.inventories.application.exception.InvalidTokenException;
import io.github.KevinMitsi.inventories.application.port.out.TokenClaims;
import io.github.KevinMitsi.inventories.application.port.out.TokenProviderPort;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link JwtAuthenticationFilter}.
 *
 * <p>No levanta contexto de Spring: {@code OncePerRequestFilter.doFilter} es un método
 * público normal, así que basta con dobles de sus dos dependencias y peticiones/respuestas
 * de {@code spring-test} para ejercitar {@code doFilterInternal} sin infraestructura.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private TokenProviderPort tokenProvider;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        // SecurityErrorResponder no se mockea: es la clase real la que se está probando
        // indirectamente en el camino de error, con un ObjectMapper real.
        SecurityErrorResponder errorResponder =
                new SecurityErrorResponder(new tools.jackson.databind.ObjectMapper());

        filter = new JwtAuthenticationFilter(tokenProvider, errorResponder);
        request = new MockHttpServletRequest("GET", "/api/v1/branches");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    private TokenClaims accessClaims() {
        return new TokenClaims(USER_ID, ORGANIZATION_ID, BRANCH_ID, RoleCode.BRANCH_MANAGER,
                "ana.torres@optiplant.co", Instant.now(), Instant.now().plus(Duration.ofHours(1)), false);
    }

    @Test
    @DisplayName("sin cabecera Authorization, deja pasar la petición sin autenticar")
    void continuesAnonymouslyWithoutHeader() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isEqualTo(request);
    }

    @Test
    @DisplayName("con un token de acceso válido, establece la identidad y continúa la cadena")
    void authenticatesWithValidAccessToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        when(tokenProvider.parseAndValidate("valid-token")).thenReturn(accessClaims());

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        assertThat(((AuthenticatedUser) authentication.getPrincipal()).userId()).isEqualTo(USER_ID);
        assertThat(chain.getRequest()).isEqualTo(request);
    }

    @Test
    @DisplayName("rechaza un token de renovación presentado como de acceso, con 401")
    void rejectsRefreshTokenUsedAsAccess() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer refresh-token");
        TokenClaims refreshClaims = new TokenClaims(USER_ID, ORGANIZATION_ID, BRANCH_ID,
                RoleCode.BRANCH_MANAGER, "ana.torres@optiplant.co",
                Instant.now(), Instant.now().plus(Duration.ofDays(7)), true);
        when(tokenProvider.parseAndValidate("refresh-token")).thenReturn(refreshClaims);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("un token inválido se rechaza con 401 y no continúa la cadena")
    void rejectsInvalidToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer garbage");
        when(tokenProvider.parseAndValidate("garbage"))
                .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.INVALID));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("un token caducado se rechaza con 401")
    void rejectsExpiredToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired");
        when(tokenProvider.parseAndValidate("expired"))
                .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("ignora una cabecera Authorization sin el prefijo Bearer")
    void ignoresHeaderWithoutBearerPrefix() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        verify(tokenProvider, never()).parseAndValidate(eq("Basic dXNlcjpwYXNz"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isEqualTo(request);
    }

    @Test
    @DisplayName("ignora un prefijo Bearer sin token")
    void ignoresEmptyBearerToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isEqualTo(request);
    }
}
