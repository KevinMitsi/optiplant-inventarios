package io.github.KevinMitsi.inventories.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos del administrador inicial, bajo {@code inventories.bootstrap}.
 *
 * <p>Resuelven el problema del arranque en frío: con la seguridad activa hace falta un
 * usuario para autenticarse, pero crear usuarios exige estar autenticado como administrador.
 * Alguien tiene que existir antes que nadie.
 *
 * <p>No se resuelve con una migración de datos porque una contraseña escrita en un fichero
 * versionado es una contraseña pública: quedaría en el repositorio, en el historial de git y
 * en cualquier copia del proyecto.
 *
 * @param enabled          si se crea el administrador cuando la instalación está vacía
 * @param organizationCode código de la organización inicial
 * @param organizationName nombre de la organización inicial
 * @param adminEmail       correo del administrador inicial
 * @param adminPassword    contraseña inicial. Debe llegar por variable de entorno; el valor
 *                         por omisión existe solo para desarrollo local y se avisa por log.
 * @param adminFirstName   nombre del administrador inicial
 * @param adminLastName    apellido del administrador inicial
 */
@ConfigurationProperties(prefix = "inventories.bootstrap")
public record BootstrapProperties(boolean enabled,
                                  String organizationCode,
                                  String organizationName,
                                  String adminEmail,
                                  String adminPassword,
                                  String adminFirstName,
                                  String adminLastName) {

    /**
     * Contraseña por omisión, solo apta para desarrollo local.
     *
     * <p>Es pública por estar aquí escrita. Si el sistema arranca con ella, se emite un
     * aviso destacado en el log, porque una instalación accesible desde fuera con esta
     * contraseña equivale a no tener contraseña.
     */
    public static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe!2026";

    public BootstrapProperties {
        organizationCode = orDefault(organizationCode, "OPTIPLANT");
        organizationName = orDefault(organizationName, "OptiPlant Consultores");
        adminEmail = orDefault(adminEmail, "admin@optiplant.local");
        adminPassword = orDefault(adminPassword, DEFAULT_ADMIN_PASSWORD);
        adminFirstName = orDefault(adminFirstName, "Administrador");
        adminLastName = orDefault(adminLastName, "General");
    }

    /** Indica si se está usando la contraseña conocida, para poder advertirlo. */
    public boolean usesDefaultPassword() {
        return DEFAULT_ADMIN_PASSWORD.equals(adminPassword);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
