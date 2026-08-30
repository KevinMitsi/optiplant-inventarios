package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import io.github.KevinMitsi.inventories.application.port.out.RoleRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link AdminBootstrapUseCase}.
 *
 * <p>Cubre la idempotencia que hace seguro reintentarlo en cada arranque: no debe duplicar
 * ni la organización por defecto ni el administrador si ya existen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminBootstrapUseCase")
class AdminBootstrapUseCaseTest {

    private static final String EMAIL = "admin@admin.com";
    private static final String RAW_PASSWORD = "admin123";
    private static final String PASSWORD_HASH = "$2a$10$hashGenerado";
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Mock
    private OrganizationRepositoryPort organizationRepository;
    @Mock
    private RoleRepositoryPort roleRepository;
    @Mock
    private UserRepositoryPort userRepository;
    @Mock
    private PasswordHasherPort passwordHasher;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @InjectMocks
    private AdminBootstrapUseCase useCase;

    private Organization organization;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        organization = Organization.create("OPTIPLANT", "OptiPlant Consultores", null, null);
        adminRole = new Role(UUID.randomUUID(), RoleCode.ADMIN, "Administrador general", null);
    }

    @Test
    @DisplayName("crea la organización por defecto y el administrador cuando no existen")
    void createsOrganizationAndAdminWhenMissing() {
        when(organizationRepository.findByCode("OPTIPLANT")).thenReturn(Optional.empty());
        when(organizationRepository.save(any(Organization.class))).thenReturn(organization);
        when(userRepository.existsByOrganizationIdAndEmail(organization.getId(), EMAIL)).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(passwordHasher.hash(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.bootstrapAdmin(EMAIL, RAW_PASSWORD);

        verify(organizationRepository).save(any(Organization.class));
        verify(userRepository).save(userCaptor.capture());

        User created = userCaptor.getValue();
        assertThat(created.getEmail()).isEqualTo(EMAIL);
        assertThat(created.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(created.getRoleCode()).isEqualTo(RoleCode.ADMIN);
        assertThat(created.getBranchId()).isNull();
        assertThat(created.getOrganizationId()).isEqualTo(organization.getId());
    }

    @Test
    @DisplayName("reutiliza la organización por defecto si ya existe")
    void reusesExistingOrganization() {
        when(organizationRepository.findByCode("OPTIPLANT")).thenReturn(Optional.of(organization));
        when(userRepository.existsByOrganizationIdAndEmail(organization.getId(), EMAIL)).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(passwordHasher.hash(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.bootstrapAdmin(EMAIL, RAW_PASSWORD);

        verify(organizationRepository, never()).save(any(Organization.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("no crea un segundo administrador si el correo ya está registrado")
    void doesNotDuplicateAdminWhenEmailAlreadyExists() {
        when(organizationRepository.findByCode("OPTIPLANT")).thenReturn(Optional.of(organization));
        when(userRepository.existsByOrganizationIdAndEmail(organization.getId(), EMAIL)).thenReturn(true);

        useCase.bootstrapAdmin(EMAIL, RAW_PASSWORD);

        verify(userRepository, never()).save(any(User.class));
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    @DisplayName("normaliza el correo a minúsculas antes de comprobar duplicados")
    void normalizesEmailBeforeChecking() {
        when(organizationRepository.findByCode("OPTIPLANT")).thenReturn(Optional.of(organization));
        when(userRepository.existsByOrganizationIdAndEmail(eq(organization.getId()), eq(EMAIL))).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(passwordHasher.hash(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.bootstrapAdmin("Admin@Admin.COM", RAW_PASSWORD);

        verify(userRepository).existsByOrganizationIdAndEmail(organization.getId(), EMAIL);
    }

    @Test
    @DisplayName("falla explícitamente si el rol ADMIN no está sembrado en el catálogo")
    void failsFastWhenAdminRoleMissing() {
        when(organizationRepository.findByCode("OPTIPLANT")).thenReturn(Optional.of(organization));
        when(userRepository.existsByOrganizationIdAndEmail(organization.getId(), EMAIL)).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.bootstrapAdmin(EMAIL, RAW_PASSWORD))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any(User.class));
    }
}
