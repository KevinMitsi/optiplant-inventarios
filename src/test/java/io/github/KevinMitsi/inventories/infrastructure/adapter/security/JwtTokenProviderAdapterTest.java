package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import io.github.KevinMitsi.inventories.application.exception.InvalidTokenException;
import io.github.KevinMitsi.inventories.application.port.out.TokenClaims;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del adaptador de tokens.
 *
 * <p>Se ejercita la implementación real de JJWT, no un doble: lo que se está comprobando es
 * precisamente que la firma se verifique y que un token manipulado se rechace, y con un
 * doble esas garantías no existirían.
 */
@DisplayName("JwtTokenProviderAdapter")
class JwtTokenProviderAdapterTest {

    private static final String SECRET = "clave-de-pruebas-suficientemente-larga-para-hmac-256";
    private static final String ISSUER = "optiplant-inventories";

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    private JwtTokenProviderAdapter provider;
    private User manager;
    private User admin;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProviderAdapter(new JwtProperties(
                SECRET, ISSUER, Duration.ofHours(1), Duration.ofDays(7)));

        manager = userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID);
        admin = userWith(RoleCode.ADMIN, null);
    }

    private User userWith(RoleCode code, UUID branchId) {
        Role role = new Role(UUID.randomUUID(), code, code.name(), null);
        return User.reconstitute(USER_ID, ORGANIZATION_ID, branchId, role,
                "Ana", "Torres", "ana.torres@optiplant.co", "$2a$10$hash",
                true, null, Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("Emisión")
    class Issuing {

        @Test
        @DisplayName("el token de acceso transporta usuario, organización, sucursal y rol")
        void accessTokenCarriesScope() {
            // Arrange & Act
            String token = provider.generateAccessToken(manager);
            TokenClaims claims = provider.parseAndValidate(token);

            // Assert
            assertThat(claims.userId()).isEqualTo(USER_ID);
            assertThat(claims.organizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(claims.branchId()).isEqualTo(BRANCH_ID);
            assertThat(claims.role()).isEqualTo(RoleCode.BRANCH_MANAGER);
            assertThat(claims.email()).isEqualTo("ana.torres@optiplant.co");
            assertThat(claims.isAccessToken()).isTrue();
        }

        @Test
        @DisplayName("el token del administrador no lleva sucursal")
        void adminTokenHasNoBranch() {
            // Arrange & Act
            String token = provider.generateAccessToken(admin);
            TokenClaims claims = provider.parseAndValidate(token);

            // Assert
            assertThat(claims.branchId())
                    .as("la ausencia significa alcance de organización, no sucursal desconocida")
                    .isNull();
            assertThat(claims.role()).isEqualTo(RoleCode.ADMIN);
        }

        @Test
        @DisplayName("el token de renovación se distingue del de acceso")
        void refreshTokenIsMarkedAsSuch() {
            // Arrange & Act
            TokenClaims accessClaims = provider.parseAndValidate(provider.generateAccessToken(manager));
            TokenClaims refreshClaims = provider.parseAndValidate(provider.generateRefreshToken(manager));

            // Assert
            assertThat(accessClaims.isAccessToken()).isTrue();
            assertThat(refreshClaims.isAccessToken()).isFalse();
            assertThat(refreshClaims.refreshToken()).isTrue();
        }

        @Test
        @DisplayName("el token de renovación vive más que el de acceso")
        void refreshTokenOutlivesAccessToken() {
            // Arrange & Act
            TokenClaims accessClaims = provider.parseAndValidate(provider.generateAccessToken(manager));
            TokenClaims refreshClaims = provider.parseAndValidate(provider.generateRefreshToken(manager));

            // Assert
            assertThat(refreshClaims.expiresAt()).isAfter(accessClaims.expiresAt());
        }

        @Test
        @DisplayName("informa la vigencia configurada del token de acceso")
        void exposesConfiguredTtl() {
            // Arrange & Act
            Duration ttl = provider.getAccessTokenTtl();

            // Assert
            assertThat(ttl).isEqualTo(Duration.ofHours(1));
        }
    }

    @Nested
    @DisplayName("Verificación")
    class Verification {

        @Test
        @DisplayName("rechaza un token firmado con otra clave")
        void rejectsTokenSignedWithAnotherKey() {
            // Arrange
            JwtTokenProviderAdapter otherProvider = new JwtTokenProviderAdapter(new JwtProperties(
                    "otra-clave-distinta-igualmente-larga-para-hmac-256", ISSUER,
                    Duration.ofHours(1), Duration.ofDays(7)));
            String foreignToken = otherProvider.generateAccessToken(manager);

            // Act & Assert
            assertThatThrownBy(() -> provider.parseAndValidate(foreignToken))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("rechaza un token de otro emisor")
        void rejectsTokenFromAnotherIssuer() {
            // Arrange
            JwtTokenProviderAdapter otherIssuer = new JwtTokenProviderAdapter(new JwtProperties(
                    SECRET, "otro-sistema", Duration.ofHours(1), Duration.ofDays(7)));
            String foreignToken = otherIssuer.generateAccessToken(manager);

            // Act & Assert
            assertThatThrownBy(() -> provider.parseAndValidate(foreignToken))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("rechaza un token cuyo contenido ha sido alterado")
        void rejectsTamperedToken() {
            // Arrange
            String token = provider.generateAccessToken(manager);
            String[] parts = token.split("\\.");
            String tamperedPayload = parts[1].substring(0, parts[1].length() - 4) + "AAAA";
            String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

            // Act & Assert
            assertThatThrownBy(() -> provider.parseAndValidate(tampered))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("informa que el token expiró, para que el cliente lo renueve")
        void reportsExpiryDistinctly() {
            // Arrange
            JwtTokenProviderAdapter expiringProvider = new JwtTokenProviderAdapter(new JwtProperties(
                    SECRET, ISSUER, Duration.ofSeconds(-60), Duration.ofSeconds(-60)));
            String expiredToken = expiringProvider.generateAccessToken(manager);

            // Act & Assert
            assertThatThrownBy(() -> provider.parseAndValidate(expiredToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("expirado");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "no-es-un-token", "a.b.c"})
        @DisplayName("rechaza cadenas que no son tokens válidos")
        void rejectsMalformedTokens(String token) {
            // Arrange, Act & Assert
            assertThatThrownBy(() -> provider.parseAndValidate(token))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    @DisplayName("Configuración")
    class Configuration {

        @Test
        @DisplayName("rechaza una clave de firma demasiado corta")
        void rejectsShortSecret() {
            // Arrange, Act & Assert
            assertThatThrownBy(() -> new JwtProperties(
                    "corta", ISSUER, Duration.ofHours(1), Duration.ofDays(7)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("al menos 32");
        }

        @Test
        @DisplayName("acepta una clave de exactamente la longitud mínima")
        void acceptsMinimumLengthSecret() {
            // Arrange
            String minimumSecret = "x".repeat(JwtProperties.MINIMUM_SECRET_LENGTH);

            // Act
            JwtProperties properties = new JwtProperties(
                    minimumSecret, ISSUER, Duration.ofHours(1), Duration.ofDays(7));

            // Assert
            assertThat(properties.secret()).hasSize(JwtProperties.MINIMUM_SECRET_LENGTH);
        }
    }
}
