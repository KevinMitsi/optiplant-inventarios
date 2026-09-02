package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import io.github.KevinMitsi.inventories.application.port.out.RoleRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Siembra el administrador inicial al arrancar la aplicación (ver README §5.4 y §11).
 *
 * <p>No vive en una migración de Flyway: una contraseña en un fichero versionado es una
 * contraseña pública. Es idempotente — solo crea la organización por defecto y el
 * administrador si todavía no existen, de modo que reiniciar la aplicación nunca
 * restablece una contraseña ya cambiada desde la API.
 */
@AuditedUseCase
public class AdminBootstrapUseCase {

    private static final Logger log = Logger.getLogger(AdminBootstrapUseCase.class.getName());

    private static final String DEFAULT_ORG_CODE = "OPTIPLANT";
    private static final String DEFAULT_ORG_NAME = "OptiPlant Consultores";

    private final OrganizationRepositoryPort organizationRepository;
    private final RoleRepositoryPort roleRepository;
    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;

    public AdminBootstrapUseCase(OrganizationRepositoryPort organizationRepository,
                                 RoleRepositoryPort roleRepository,
                                 UserRepositoryPort userRepository,
                                 PasswordHasherPort passwordHasher) {
        this.organizationRepository = organizationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public void bootstrapAdmin(String adminEmail, String rawPassword) {
        Organization organization = organizationRepository.findByCode(DEFAULT_ORG_CODE)
                .orElseGet(this::createDefaultOrganization);

        String email = normalizeEmail(adminEmail);
        if (userRepository.existsByOrganizationIdAndEmail(organization.getId(), email)) {
            log.info(() -> "Administrador inicial ya existe (%s); no se vuelve a crear.".formatted(email));
            return;
        }

        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol ADMIN no está sembrado en app_role (falta la migración de datos de referencia)."));

        String passwordHash = passwordHasher.hash(rawPassword);
        User admin = User.create(
                organization.getId(), null, adminRole, "Admin", "Sistema", email, passwordHash);
        User saved = userRepository.save(admin);

        log.info(() -> "Administrador inicial creado: id=%s, email=%s".formatted(saved.getId(), saved.getEmail()));
    }

    private Organization createDefaultOrganization() {
        Organization organization = Organization.create(DEFAULT_ORG_CODE, DEFAULT_ORG_NAME, null, null);
        Organization saved = organizationRepository.save(organization);
        log.info(() -> "Organización por defecto creada: id=%s, code=%s".formatted(saved.getId(), saved.getCode()));
        return saved;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
