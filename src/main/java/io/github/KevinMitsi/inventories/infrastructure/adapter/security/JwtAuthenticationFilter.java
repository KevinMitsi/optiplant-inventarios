package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import io.github.KevinMitsi.inventories.application.exception.InvalidTokenException;
import io.github.KevinMitsi.inventories.application.port.out.TokenClaims;
import io.github.KevinMitsi.inventories.application.port.out.TokenProviderPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Verifica el token de cada petición y establece la identidad del solicitante.
 *
 * <p>Extiende {@code OncePerRequestFilter} para garantizar una sola ejecución por petición:
 * un reenvío interno volvería a atravesar la cadena y repetiría la verificación sin motivo.
 *
 * <p><b>Un token ausente no es un error.</b> El filtro deja el contexto vacío y cede el
 * paso; será la cadena de autorización quien decida si esa ruta exigía identificarse. Así el
 * mismo filtro sirve para rutas públicas y privadas sin conocer cuáles son cuáles.
 *
 * <p><b>Un token presente pero inválido sí lo es.</b> Se responde 401 de inmediato en lugar
 * de continuar como anónimo: seguir adelante produciría un 403 confuso —"no tienes
 * permiso"— cuando el problema real es que la sesión caducó y basta con renovarla.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProviderPort tokenProvider;
    private final SecurityErrorResponder errorResponder;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            TokenClaims claims = tokenProvider.parseAndValidate(token);

            // Un token de renovación no autoriza operaciones: solo sirve para pedir uno de
            // acceso. Sin esta comprobación, el token de vida larga valdría como el de vida
            // corta y la separación entre ambos dejaría de tener sentido.
            if (!claims.isAccessToken()) {
                log.warn("Se presentó un token de renovación en {}", request.getRequestURI());
                errorResponder.writeError(request, response,
                        new InvalidTokenException(InvalidTokenException.Reason.WRONG_TYPE));
                return;
            }

            authenticate(request, claims);
            filterChain.doFilter(request, response);

        } catch (InvalidTokenException cause) {
            // El manejador global de excepciones no alcanza a los filtros: se ejecutan antes
            // de que exista un controlador al que asociar el error. Por eso la respuesta se
            // compone aquí, con el mismo formato que el resto de la API.
            SecurityContextHolder.clearContext();
            errorResponder.writeError(request, response, cause);
        }
    }

    private void authenticate(HttpServletRequest request, TokenClaims claims) {
        AuthenticatedUser principal = AuthenticatedUser.from(claims);

        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(claims.role().asAuthority())));

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** Extrae el token de la cabecera {@code Authorization}, o devuelve nulo si no viene. */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
