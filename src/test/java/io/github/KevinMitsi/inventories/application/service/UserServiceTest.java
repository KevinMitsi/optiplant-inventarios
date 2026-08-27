package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.InvalidCredentialsException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.ChangePasswordCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateUserCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReassignUserCommand;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import io.github.KevinMitsi.inventories.application.port.out.RoleRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link UserService}.
 *
 * <p>El grueso se dedica al invariante que ninguna restricción de la base puede expresar:
 * que la organización nunca se quede sin un administrador activo. Se comprueban los dos
 * caminos que llevan al mismo sitio —dar de baja y degradar de rol—, porque cerrar solo uno
 * dejaría el otro abierto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserService")
class UserServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "ana.torres@optiplant.co";
    private static final String PASSWORD_HASH = "$2a$10$hashAlmacenado";

    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private RoleRepositoryPort roleRepository;
    @Mock
    private BranchRepositoryPort branchRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;
    @Mock
    private PasswordHasherPort passwordHasher;

    @InjectMocks
    private UserService service;

    private Role adminRole;
    private Role managerRole;
    private Branch branch;

    @BeforeEach
    void setUp() {
        adminRole = new Role(UUID.randomUUID(), RoleCode.ADMIN, "Administrador general", null);
        managerRole = new Role(UUID.randomUUID(), RoleCode.BRANCH_MANAGER, "Gerente de sucursal", null);

        branch = Branch.reconstitute(BRANCH_ID, ORGANIZATION_ID, "BOG-01", "Sucursal Chapinero",
                null, null, null, null, true, Instant.now(), Instant.now());

        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByCode(RoleCode.BRANCH_MANAGER)).thenReturn(Optional.of(managerRole));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(passwordHasher.hash(anyString())).thenReturn(PASSWORD_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    private User userWith(RoleCode code, UUID branchId) {
        Role role = code == RoleCode.ADMIN ? adminRole : managerRole;
        return User.reconstitute(USER_ID, ORGANIZATION_ID, branchId, role,
                "Ana", "Torres", EMAIL, PASSWORD_HASH, true, null, Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("Alta")
    class Creation {

        private CreateUserCommand command(RoleCode role, UUID branchId) {
            return new CreateUserCommand(ORGANIZATION_ID, branchId, role,
                    "Ana", "Torres", EMAIL, "MiClaveSegura2026");
        }

        @Test
        @DisplayName("crea el usuario cifrando la contraseña")
        void createsUserHashingPassword() {
            when(userRepository.existsByOrganizationIdAndEmail(ORGANIZATION_ID, EMAIL))
                    .thenReturn(false);

            User created = service.createUser(command(RoleCode.BRANCH_MANAGER, BRANCH_ID));

            verify(passwordHasher).hash("MiClaveSegura2026");
            assertThat(created.getPasswordHash())
                    .as("el agregado solo recibe el hash, nunca la contraseña en claro")
                    .isEqualTo(PASSWORD_HASH);
        }

        @Test
        @DisplayName("normaliza el correo antes de comprobar duplicados")
        void normalizesEmailBeforeDuplicateCheck() {
            when(userRepository.existsByOrganizationIdAndEmail(ORGANIZATION_ID, EMAIL))
                    .thenReturn(false);

            service.createUser(new CreateUserCommand(ORGANIZATION_ID, BRANCH_ID,
                    RoleCode.BRANCH_MANAGER, "Ana", "Torres",
                    "  Ana.Torres@OptiPlant.CO ", "MiClaveSegura2026"));

            verify(userRepository).existsByOrganizationIdAndEmail(ORGANIZATION_ID, EMAIL);
        }

        @Test
        @DisplayName("falla si el correo ya está registrado en la organización")
        void failsOnDuplicateEmail() {
            when(userRepository.existsByOrganizationIdAndEmail(ORGANIZATION_ID, EMAIL))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createUser(command(RoleCode.BRANCH_MANAGER, BRANCH_ID)))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("falla si la organización no existe")
        void failsWhenOrganizationMissing() {
            when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.createUser(command(RoleCode.BRANCH_MANAGER, BRANCH_ID)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("rechaza asignar una sucursal de otra organización")
        void rejectsBranchFromAnotherOrganization() {
            Branch foreignBranch = Branch.reconstitute(BRANCH_ID, UUID.randomUUID(), "MED-01",
                    "Sucursal ajena", null, null, null, null, true, Instant.now(), Instant.now());
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(foreignBranch));
            when(userRepository.existsByOrganizationIdAndEmail(ORGANIZATION_ID, EMAIL))
                    .thenReturn(false);

            // La clave foránea garantiza que la sucursal existe, no que sea la correcta.
            // Sin esta comprobación se daría acceso a inventario de otra empresa.
            assertThatThrownBy(() -> service.createUser(command(RoleCode.BRANCH_MANAGER, BRANCH_ID)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("otra organización");
        }

        @Test
        @DisplayName("rechaza asignar un usuario a una sucursal dada de baja")
        void rejectsInactiveBranch() {
            branch.deactivate();
            when(userRepository.existsByOrganizationIdAndEmail(ORGANIZATION_ID, EMAIL))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.createUser(command(RoleCode.BRANCH_MANAGER, BRANCH_ID)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("dada de baja");
        }
    }

    @Nested
    @DisplayName("Protección del último administrador")
    class LastAdminProtection {

        @Test
        @DisplayName("no permite dar de baja al último administrador activo")
        void cannotDeactivateLastAdmin() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWith(RoleCode.ADMIN, null)));
            when(userRepository.countActiveAdmins(ORGANIZATION_ID)).thenReturn(1L);

            // Sin ningún administrador, nadie podría gestionar usuarios ni sucursales, y
            // habría que intervenir la base de datos a mano para recuperar el sistema.
            assertThatThrownBy(() -> service.deactivateUser(USER_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("último administrador");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("permite dar de baja a un administrador si queda otro activo")
        void canDeactivateAdminWhenAnotherExists() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWith(RoleCode.ADMIN, null)));
            when(userRepository.countActiveAdmins(ORGANIZATION_ID)).thenReturn(2L);

            User result = service.deactivateUser(USER_ID);

            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("no permite degradar al último administrador activo")
        void cannotDemoteLastAdmin() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWith(RoleCode.ADMIN, null)));
            when(userRepository.countActiveAdmins(ORGANIZATION_ID)).thenReturn(1L);

            // Es el mismo riesgo que darlo de baja, por otra puerta. Cerrar solo una
            // dejaría la otra abierta.
            assertThatThrownBy(() -> service.reassign(
                    new ReassignUserCommand(USER_ID, RoleCode.BRANCH_MANAGER, BRANCH_ID)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("último administrador");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("dar de baja a un usuario que no es administrador no consulta el conteo")
        void deactivatingNonAdminSkipsTheCheck() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID)));

            service.deactivateUser(USER_ID);

            verify(userRepository, never()).countActiveAdmins(any());
        }

        @Test
        @DisplayName("promover a administrador no dispara la comprobación")
        void promotingToAdminSkipsTheCheck() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID)));

            assertThatCode(() -> service.reassign(
                    new ReassignUserCommand(USER_ID, RoleCode.ADMIN, null)))
                    .doesNotThrowAnyException();

            verify(userRepository, never()).countActiveAdmins(any());
        }
    }

    @Nested
    @DisplayName("Cambio de contraseña")
    class PasswordChange {

        @Test
        @DisplayName("sustituye el hash cuando la contraseña actual coincide")
        void changesPassword() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID)));
            when(passwordHasher.matches("actual", PASSWORD_HASH)).thenReturn(true);
            when(passwordHasher.hash("nueva-clave-2026")).thenReturn("$2a$10$hashNuevo");

            service.changePassword(new ChangePasswordCommand(USER_ID, "actual", "nueva-clave-2026"));

            verify(passwordHasher).hash("nueva-clave-2026");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("falla si la contraseña actual no coincide, y no cifra la nueva")
        void failsWhenCurrentPasswordIsWrong() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID)));
            when(passwordHasher.matches("incorrecta", PASSWORD_HASH)).thenReturn(false);

            // Exigir la contraseña actual protege frente a que un token robado, o una sesión
            // dejada abierta, basten para apoderarse de la cuenta.
            assertThatThrownBy(() -> service.changePassword(
                    new ChangePasswordCommand(USER_ID, "incorrecta", "nueva-clave-2026")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordHasher, never()).hash(anyString());
            verify(userRepository, never()).save(any());
        }
    }
}
