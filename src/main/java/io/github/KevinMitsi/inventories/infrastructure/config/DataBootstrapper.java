package io.github.KevinMitsi.inventories.infrastructure.config;

import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import io.github.KevinMitsi.inventories.application.port.out.RoleRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea la organización y el administrador iniciales cuando la instalación está vacía.
 *
 * <p>Resuelve el arranque en frío: con la seguridad activa hace falta un usuario para
 * autenticarse, pero crear usuarios exige estar autenticado como administrador. Alguien
 * tiene que existir antes que nadie.
 *
 * <p><b>Por qué aquí y no en una migración de Flyway.</b> Una contraseña escrita en un
 * fichero versionado es una contraseña pública: quedaría en el repositorio, en el historial
 * de git y en cualquier copia del proyecto. Desde aquí puede llegar por variable de entorno
 * y cifrarse con el mismo algoritmo que cualquier otra.
 *
 * <p><b>Solo actúa si no existe ningún administrador.</b> No es un cargador de datos de
 * ejemplo ni reescribe nada: en una instalación con usuarios no hace absolutamente nada, así
 * que arrancar de nuevo no puede restablecer una contraseña ya cambiada.
 *
 * <p>Se ejecuta como {@code ApplicationRunner}, es decir, después de que Flyway haya
 * aplicado las migraciones y el catálogo de roles exista.
 */
@Component
@ConditionalOnProperty(prefix = "inventories.bootstrap", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BootstrapProperties.class)
@RequiredArgsConstructor
public class DataBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataBootstrapper.class);

    private final OrganizationRepositoryPort organizationRepository;
    private final UserRepositoryPort userRepository;
    private final RoleRepositoryPort roleRepository;
    private final PasswordHasherPort passwordHasher;
    private final BootstrapProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        Organization organization = findOrCreateOrganization();

        // Si ya hay algún administrador, la instalación está inicializada. Salir aquí es lo
        // que impide que un reinicio restablezca una contraseña que alguien ya cambió.
        if (userRepository.countActiveAdmins(organization.getId()) > 0) {
            log.debug("La instalación ya tiene un administrador activo; no se inicializa nada.");
            return;
        }

        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "El catálogo de roles está vacío. La migración V2__reference_data.sql "
                                + "debe haberse aplicado antes de inicializar el administrador."));

        User admin = User.create(
                organization.getId(),
                // El administrador general no lleva sucursal: su ámbito es toda la
                // organización, y asignarle una haría ambiguo su alcance (RN-12).
                null,
                adminRole,
                properties.adminFirstName(),
                properties.adminLastName(),
                properties.adminEmail(),
                passwordHasher.hash(properties.adminPassword()));

        userRepository.save(admin);

        log.info("Administrador inicial creado: {}", admin.getEmail());
        warnIfDefaultPasswordInUse();
    }

    private Organization findOrCreateOrganization() {
        return organizationRepository.findByCode(properties.organizationCode())
                .orElseGet(() -> {
                    Organization created = organizationRepository.save(Organization.create(
                            properties.organizationCode(),
                            properties.organizationName(),
                            null,
                            null));
                    log.info("Organización inicial creada: {} ({})",
                            created.getName(), created.getCode());
                    return created;
                });
    }

    /**
     * Avisa si el administrador quedó con la contraseña conocida.
     *
     * <p>El aviso es deliberadamente ruidoso. Una instalación accesible desde fuera con una
     * contraseña que está escrita en el código fuente equivale a no tener contraseña, y ese
     * es el tipo de detalle que pasa inadvertido en una línea de log discreta.
     */
    private void warnIfDefaultPasswordInUse() {
        if (!properties.usesDefaultPassword()) {
            return;
        }

        log.warn("""

                ===============================================================================
                  AVISO DE SEGURIDAD

                  El administrador inicial se creó con la contraseña por defecto, que está
                  escrita en el código fuente y por tanto es pública.

                  Antes de exponer esta instalación fuera de un entorno local:
                    1. Defina la variable de entorno BOOTSTRAP_ADMIN_PASSWORD, o
                    2. Cambie la contraseña desde POST /api/v1/users/{id}/password

                  Compruebe también que JWT_SECRET no sea el valor por defecto: con la clave
                  de firma conocida, cualquiera puede emitir tokens válidos para cualquier rol.
                ===============================================================================
                """);
    }
}
