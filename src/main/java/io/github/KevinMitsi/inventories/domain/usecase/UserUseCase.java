package io.github.KevinMitsi.inventories.domain.usecase;

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
import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@AuditedUseCase
public class UserUseCase implements ManageUserUseCase, QueryUserUseCase {

    private static final Logger log = Logger.getLogger(UserUseCase.class.getName());

    private static final String USER = "el usuario";
    private static final String ROLE = "el rol";
    private static final String BRANCH = "la sucursal";
    private static final String ORGANIZATION = "la organización";

    private final UserRepositoryPort userRepository;
    private final RoleRepositoryPort roleRepository;
    private final BranchRepositoryPort branchRepository;
    private final OrganizationRepositoryPort organizationRepository;
    private final PasswordHasherPort passwordHasher;

    public UserUseCase(UserRepositoryPort userRepository,
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
        log.info(() -> "Usuario creado: id=%s, rol=%s, sucursal=%s"
                .formatted(saved.getId(), saved.getRoleCode(), saved.getBranchId()));
        return saved;
    }

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

        boolean losesAdminRole =
                user.getRoleCode() == RoleCode.ADMIN && command.role() != RoleCode.ADMIN;
        if (losesAdminRole) {
            requireAnotherActiveAdminExists(user, "cambiar el rol del");
        }

        user.reassign(newRole, command.branchId());

        User saved = userRepository.save(user);
        log.info(() -> "Usuario reasignado: id=%s, rol=%s, sucursal=%s"
                .formatted(saved.getId(), saved.getRoleCode(), saved.getBranchId()));
        return saved;
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        User user = loadUser(command.userId());

        if (!passwordHasher.matches(command.currentPassword(), user.getPasswordHash())) {
            log.warning(() -> "Cambio de contraseña rechazado para el usuario %s: la actual no coincide"
                    .formatted(user.getId()));
            throw new InvalidCredentialsException();
        }

        user.changePassword(passwordHasher.hash(command.newPassword()));
        userRepository.save(user);

        log.info(() -> "Contraseña actualizada para el usuario %s".formatted(user.getId()));
    }

    @Override
    public User deactivateUser(UUID userId) {
        User user = loadUser(userId);

        if (user.getRoleCode() == RoleCode.ADMIN && user.isActive()) {
            requireAnotherActiveAdminExists(user, "desactivar al");
        }

        user.deactivate();

        User saved = userRepository.save(user);
        log.info(() -> "Usuario desactivado: id=%s".formatted(saved.getId()));
        return saved;
    }

    @Override
    public User activateUser(UUID userId) {
        User user = loadUser(userId);
        user.activate();
        return userRepository.save(user);
    }

    @Override
    public User getUserById(UUID userId) {
        return loadUser(userId);
    }

    @Override
    public PageResult<User> searchUsers(UserSearchCriteria criteria, PageQuery pageQuery) {
        return userRepository.search(criteria, pageQuery);
    }

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

    private void validateBranchBelongsToOrganization(UUID branchId,
                                                     UUID organizationId,
                                                     Role role) {
        if (branchId == null) {
            return;
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException(BRANCH, branchId));

        if (!branch.getOrganizationId().equals(organizationId)) {
            throw new BusinessRuleViolationException("RF-04",
                    "La sucursal indicada pertenece a otra organización.",
                    Map.of("branchId", String.valueOf(branchId),
                            "expectedOrganizationId", String.valueOf(organizationId)));
        }

        if (!branch.isActive() && role.code().requiresBranch()) {
            throw new BusinessRuleViolationException("RF-04",
                    "No se puede asignar un usuario a una sucursal dada de baja.",
                    Map.of("branchId", String.valueOf(branchId)));
        }
    }

    /** Cuenta al propio usuario, aún activo en este punto: el umbral es <= 1, no == 0. */
    private void requireAnotherActiveAdminExists(User user, String operation) {
        long activeAdmins = userRepository.countActiveAdmins(user.getOrganizationId());

        if (activeAdmins <= 1) {
            throw new BusinessRuleViolationException("RF-03",
                    ("No se puede %s usuario: es el último administrador activo de la "
                            + "organización, y dejarla sin ninguno impediría gestionar "
                            + "usuarios y sucursales.").formatted(operation),
                    Map.of("userId", String.valueOf(user.getId()),
                            "activeAdmins", activeAdmins));
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
