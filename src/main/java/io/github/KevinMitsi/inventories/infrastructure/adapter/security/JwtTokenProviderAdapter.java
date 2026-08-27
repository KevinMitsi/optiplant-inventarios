package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import io.github.KevinMitsi.inventories.application.exception.InvalidTokenException;
import io.github.KevinMitsi.inventories.application.port.out.TokenClaims;
import io.github.KevinMitsi.inventories.application.port.out.TokenProviderPort;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Adaptador que satisface {@link TokenProviderPort} con JJWT.
 *
 * <p>Es la única clase que conoce el formato JWT y la biblioteca que lo implementa. Los
 * casos de uso piden "un token para este usuario" y reciben una cadena.
 *
 * <p><b>Qué transporta el token y por qué.</b> Además del sujeto, incorpora organización,
 * sucursal y rol. Eso permite resolver la autorización de cada petición sin consultar la
 * base de datos, que es lo que hace viable una API sin estado y escalable en horizontal
 * (RNF-08).
 *
 * <p><b>El precio de esa decisión.</b> Los datos del token son una fotografía del instante
 * en que se emitió. Si a un usuario se le cambia el rol o se le da de baja, su token de
 * acceso sigue siendo válido hasta que caduque. Se mitiga con dos medidas: el token de
 * acceso vive poco (una hora por omisión), y la renovación vuelve a cargar el usuario desde
 * la base en lugar de fiarse de lo que el token afirma.
 */
@Component
public class JwtTokenProviderAdapter implements TokenProviderPort {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProviderAdapter.class);

    private static final String CLAIM_ORGANIZATION = "org";
    private static final String CLAIM_BRANCH = "branch";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "typ";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProviderAdapter(JwtProperties properties) {
        this.properties = properties;
        // La validación de longitud vive en JwtProperties, así que una clave insuficiente
        // impide arrancar en lugar de fallar en el primer intento de acceso.
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateAccessToken(User user) {
        return buildToken(user, TYPE_ACCESS, properties.accessTokenTtl());
    }

    @Override
    public String generateRefreshToken(User user) {
        return buildToken(user, TYPE_REFRESH, properties.refreshTokenTtl());
    }

    @Override
    public Duration getAccessTokenTtl() {
        return properties.accessTokenTtl();
    }

    @Override
    public TokenClaims parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    // Exigir el emisor rechaza tokens firmados con la misma clave por otro
                    // sistema, en caso de que la clave llegara a compartirse.
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return toTokenClaims(claims);

        } catch (ExpiredJwtException cause) {
            // La caducidad es la única causa que se comunica con precisión: el cliente debe
            // reaccionar renovando la sesión, no volviendo a pedir credenciales.
            throw new InvalidTokenException(InvalidTokenException.Reason.EXPIRED, cause);

        } catch (JwtException | IllegalArgumentException cause) {
            // Firma inválida, formato corrupto, emisor distinto. Se registra el motivo en el
            // servidor pero no se detalla al cliente: describiría a quien manipula un token
            // en qué punto falló su intento.
            log.warn("Token rechazado: {}", cause.getMessage());
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, cause);
        }
    }

    private String buildToken(User user, String type, Duration ttl) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_ORGANIZATION, user.getOrganizationId().toString())
                .claim(CLAIM_ROLE, user.getRoleCode().name())
                .claim(CLAIM_EMAIL, user.getEmail());

        // El administrador general no tiene sucursal: se omite el dato en lugar de escribir
        // un nulo, para que su ausencia signifique inequívocamente "alcance de organización".
        if (user.getBranchId() != null) {
            builder.claim(CLAIM_BRANCH, user.getBranchId().toString());
        }

        return builder.signWith(signingKey).compact();
    }

    private TokenClaims toTokenClaims(Claims claims) {
        return new TokenClaims(
                parseUuid(claims.getSubject()),
                parseUuid(claims.get(CLAIM_ORGANIZATION, String.class)),
                parseNullableUuid(claims.get(CLAIM_BRANCH, String.class)),
                RoleCode.fromString(claims.get(CLAIM_ROLE, String.class)),
                claims.get(CLAIM_EMAIL, String.class),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant(),
                TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class)));
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException cause) {
            // La firma era válida pero el contenido no encaja con lo que este sistema emite.
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID, cause);
        }
    }

    private UUID parseNullableUuid(String value) {
        return value == null ? null : parseUuid(value);
    }
}
