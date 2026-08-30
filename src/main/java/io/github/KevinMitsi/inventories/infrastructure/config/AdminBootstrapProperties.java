package io.github.KevinMitsi.inventories.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Credenciales del administrador inicial, bajo {@code inventories.bootstrap.admin}.
 *
 * <p>Los valores por omisión ({@code admin@admin.com} / {@code admin123}) son los que exige
 * el entregable de la prueba técnica; en cualquier despliegue real deben sobrescribirse con
 * {@code BOOTSTRAP_ADMIN_EMAIL} / {@code BOOTSTRAP_ADMIN_PASSWORD}, igual que ocurre con
 * {@link JwtProperties#secret()}.
 *
 * @param email    correo del administrador inicial
 * @param password contraseña en claro; solo pasa por memoria el tiempo de cifrarla
 */
@Validated
@ConfigurationProperties(prefix = "inventories.bootstrap.admin")
public record AdminBootstrapProperties(

        @NotBlank(message = "El correo del administrador inicial es obligatorio.")
        String email,

        @NotBlank(message = "La contraseña del administrador inicial es obligatoria.")
        String password
) {
}
