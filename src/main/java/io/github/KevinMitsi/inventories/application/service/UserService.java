package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.InvalidCredentialsException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.ChangePasswordCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateUserCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReassignUserCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateUserProfileCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.UserSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import io.github.KevinMitsi.inventories.application.port.out.RoleRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Administración y consulta de usuarios (HU-02, HU-03, RF-03, RF-04).
 *
 * <p>Cuida un invariante que ninguna restricción de la base puede expresar: <b>la
 * organización nunca se queda sin un administrador activo</b>. Sin él, nadie podría crear
 * usuarios, dar de alta sucursales ni recuperar el sistema desde la propia aplicación:
 * habría que intervenir la base de datos a mano. La comprobación aplica tanto al dar de
 * baja como al degradar de rol, porque ambos caminos llevan al mismo sitio.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class UserService implements ManageUserUseCase, QueryUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String USER = "el usuario";
    private static final String ROLE = "el rol";
    private static final String BRANCH = "la sucursal";
    private static final String ORGANIZATION = "la organización";

    private final UserRepositoryPort userRepository;
    private final RoleRepositoryPort roleRepository;
    private final BranchRepositoryPort branchRepository;
    private final OrganizationRepositoryPort organizationRepository;
    private final PasswordHasherPort passwordHasher;

    public UserService(UserRepositoryPort userRepository,
                       RoleRepositoryPort roleRepository,
                       BranchRepositoryPort branchRepository,
                       OrganizationRepositoryPort organizationRepository,
                       PasswordHasherPort passwordHasher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.branchRepository = branchRepository;
        this.organizationRepository = organizationRepository;
        this.passwordHasher = passwordHasher;
    }

    // ----------------------------------------------------------------------------------
    // Alta
    // ----------------------------------------------------------------------------------

    @Override
    public User createUser(CreateUserCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }

        String email = normalizeEmail(command.email());
        if (userRepository.existsByOrganizationIdAndEmail(command.organizationId(), email)) {
            throw new DuplicateResourceException(USER, "correo electrónico", email);
        }

        Role role = loadRole(command.role());
        validateBranchBelongsToOrganization(command.branchId(), command.organizationId(), role);

        // La contraseña se cifra aquí: es el último punto en que existe en claro. El
        // agregado solo recibe el hash, de modo que no puede filtrarla por accidente.
        String passwordHash = passwordHasher.hash(command.rawPassword());

        User user = User.create(
                command.organizationId(),
                command.branchId(),
                role,
                command.firstName(),
                command.lastName(),
                email,
                passwordHash);

        User saved = userRepository.save(user);
        log.info("Usuario creado: id={}, rol={}, sucursal={}",
                saved.getId(), saved.getRoleCode(), saved.getBranchId());
        return saved;
    }

    // ----------------------------------------------------------------------------------
    // Modificación
    // ----------------------------------------------------------------------------------

    @Override
    public User updateProfile(UpdateUserProfileCommand command) {
        User user = loadUser(command.userId());
        user.updateProfile(command.firstName(), command.lastName());
        return userRepository.save(user);
    }

    @Override
    public User reassign(ReassignUserCommand command) {
        User user = loadUser(command.userId());
        Role newRole = loadRole(command.role());

        validateBranchBelongsToOrganization(
                command.branchId(), user.getOrganizationId(), newRole);

        // Degradar al último administrador deja la organización sin nadie capaz de
        // gestionarla. Es el mismo riesgo que darlo de baja, por otra puerta.
        boolean losesAdminRole =
                user.getRoleCode() == RoleCode.ADMIN && command.role() != RoleCode.ADMIN;
        if (losesAdminRole) {
            requireAnotherActiveAdminExists(user, "cambiar el rol del");
        }

        user.reassign(newRole, command.branchId());

        User saved = userRepository.save(user);
        log.info("Usuario reasignado: id={}, rol={}, sucursal={}",
                saved.getId(), saved.getRoleCode(), saved.getBranchId());
        return saved;
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        User user = loadUser(command.userId());

        // Estar autenticado no basta: sin esta comprobación, un token robado o una sesión
        // abierta permitirían apoderarse de la cuenta cambiándole la contraseña.
        if (!passwordHasher.matches(command.currentPassword(), user.getPasswordHash())) {
            log.warn("Cambio de contraseña rechazado para el usuario {}: la actual no coincide",
                    user.getId());
            throw new InvalidCredentialsException();
        }

        user.changePassword(passwordHasher.hash(command.newPassword()));
        userRepository.save(user);

        log.info("Contraseña actualizada para el usuario {}", user.getId());
    }

    // ----------------------------------------------------------------------------------
    // Estado de la cuenta
    // ----------------------------------------------------------------------------------

    @Override
    public User deactivateUser(UUID userId) {
        User user = loadUser(userId);

        if (user.getRoleCode() == RoleCode.ADMIN && user.isActive()) {
            requireAnotherActiveAdminExists(user, "desactivar al");
        }

        user.deactivate();

        User saved = userRepository.save(user);
        log.info("Usuario desactivado: id={}", saved.getId());
        return saved;
    }

    @Override
    public User activateUser(UUID userId) {
        User user = loadUser(userId);
        user.activate();
        return userRepository.save(user);
    }

    // ----------------------------------------------------------------------------------
    // Consulta
    // ----------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public User getUserById(UUID userId) {
        return loadUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> searchUsers(UserSearchCriteria criteria, PageQuery pageQuery) {
        return userRepository.search(criteria, pageQuery);
    }

    // ----------------------------------------------------------------------------------
    // Apoyo
    // ----------------------------------------------------------------------------------

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER, userId));
    }

    private Role loadRole(RoleCode code) {
        if (code == null) {
            throw new ResourceNotFoundException(ROLE, "código", null);
        }
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(ROLE, "código", code));
    }

    /**
     * Comprueba que la sucursal existe y pertenece a la misma organización que el usuario.
     *
     * <p>La segunda parte no es redundante con la clave foránea: la base garantiza que la
     * sucursal existe, no que sea de la organización correcta. Sin esta comprobación se
     * podría asignar un operador a una sucursal ajena y darle acceso a inventario que no le
     * corresponde.
     */
    private void validateBranchBelongsToOrganization(UUID branchId,
                                                     UUID organizationId,
                                                     Role role) {
        if (branchId == null) {
            // Que el administrador no lleve sucursal y los demás roles sí lo exige el
            // agregado User, que es donde vive ese invariante.
            return;
        }

        var branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException(BRANCH, branchId));

        if (!branch.getOrganizationId().equals(organizationId)) {
            throw new BusinessRuleViolationException("RF-04",
                    "La sucursal indicada pertenece a otra organización.",
                    java.util.Map.of(
                            "branchId", String.valueOf(branchId),
                            "expectedOrganizationId", String.valueOf(organizationId)));
        }

        if (!branch.isActive() && role.code().requiresBranch()) {
            throw new BusinessRuleViolationException("RF-04",
                    "No se puede asignar un usuario a una sucursal dada de baja.",
                    java.util.Map.of("branchId", String.valueOf(branchId)));
        }
    }

    /**
     * Exige que quede al menos otro administrador activo además del afectado.
     *
     * <p>El conteo incluye al propio usuario, que todavía está activo en este punto, de ahí
     * que el umbral sea {@code <= 1} y no {@code == 0}.
     */
    private void requireAnotherActiveAdminExists(User user, String operation) {
        long activeAdmins = userRepository.countActiveAdmins(user.getOrganizationId());

        if (activeAdmins <= 1) {
            throw new BusinessRuleViolationException("RF-03",
                    ("No se puede %s usuario: es el último administrador activo de la "
                            + "organización, y dejarla sin ninguno impediría gestionar "
                            + "usuarios y sucursales.").formatted(operation),
                    java.util.Map.of(
                            "userId", String.valueOf(user.getId()),
                            "activeAdmins", activeAdmins));
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
