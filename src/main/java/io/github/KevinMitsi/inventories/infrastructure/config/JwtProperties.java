package io.github.KevinMitsi.inventories.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Parámetros de emisión y verificación de tokens, bajo {@code inventories.security.jwt}.
 *
 * <p>Se declaran como propiedades tipadas y validadas en lugar de leerse con
 * {@code @Value} disperso: así una configuración incorrecta impide arrancar la aplicación
 * en lugar de manifestarse al primer intento de acceso.
 *
 * @param secret          clave de firma HMAC. Debe venir de una variable de entorno; el
 *                        valor por omisión solo sirve para desarrollo local.
 * @param issuer          emisor que se estampa en el token y se exige al verificarlo
 * @param accessTokenTtl  vida del token de acceso. Corta a propósito: mientras esté vigente,
 *                        una baja o un cambio de rol no surten efecto sobre él.
 * @param refreshTokenTtl vida del token de renovación, que no autoriza operaciones
 */
@Validated
@ConfigurationProperties(prefix = "inventories.security.jwt")
public record JwtProperties(

        @NotBlank(message = "La clave de firma JWT es obligatoria.")
        String secret,

        @NotBlank(message = "El emisor del token es obligatorio.")
        String issuer,

        @NotNull(message = "La vigencia del token de acceso es obligatoria.")
        Duration accessTokenTtl,

        @NotNull(message = "La vigencia del token de renovación es obligatoria.")
        Duration refreshTokenTtl
) {

    /**
     * Longitud mínima de la clave, en bytes.
     *
     * <p>HMAC-SHA256 exige una clave de al menos 256 bits. Una más corta debilita la firma
     * hasta hacerla susceptible de fuerza bruta, y con ella cualquiera podría emitir tokens
     * válidos para cualquier usuario y cualquier rol.
     */
    public static final int MINIMUM_SECRET_LENGTH = 32;

    public JwtProperties {
        if (secret != null && secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    ("La clave de firma JWT debe tener al menos %d caracteres; la configurada "
                            + "tiene %d. Una clave más corta permitiría falsificar tokens.")
                            .formatted(MINIMUM_SECRET_LENGTH, secret.length()));
        }
    }
}
