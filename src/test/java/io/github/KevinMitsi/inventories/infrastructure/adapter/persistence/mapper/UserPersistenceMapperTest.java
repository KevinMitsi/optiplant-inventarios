package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.RoleJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UserJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserPersistenceMapper")
class UserPersistenceMapperTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final String PASSWORD_HASH = "$2a$10$hashAlmacenado";

    private UserPersistenceMapper mapper;
    private Role managerRole;

    @BeforeEach
    void setUp() {
        mapper = new UserPersistenceMapperImpl();
        managerRole = new Role(UUID.randomUUID(), RoleCode.BRANCH_MANAGER, "Gerente de sucursal", null);
    }

    @Test
    @DisplayName("conserva todos los campos en el viaje de ida y vuelta")
    void roundTripPreservesFields() {
        // Arrange
        Instant lastLogin = Instant.parse("2026-08-27T08:14:03Z");
        User original = User.reconstitute(UUID.randomUUID(), ORGANIZATION_ID, BRANCH_ID, managerRole,
                "Ana", "Torres", "ana.torres@optiplant.co", PASSWORD_HASH,
                true, lastLogin, Instant.parse("2026-01-15T09:30:00Z"), Instant.now());

        // Act
        User result = mapper.toDomain(mapper.toEntity(original));

        // Assert
        assertThat(result.getId()).isEqualTo(original.getId());
        assertThat(result.getEmail()).isEqualTo("ana.torres@optiplant.co");
        assertThat(result.getRoleCode()).isEqualTo(RoleCode.BRANCH_MANAGER);
        assertThat(result.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(result.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(result.getLastLoginAt()).isEqualTo(lastLogin);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("el administrador viaja sin sucursal asignada")
    void adminRoundTripHasNoBranch() {
        // Arrange
        Role adminRole = new Role(UUID.randomUUID(), RoleCode.ADMIN, "Administrador general", null);
        User admin = User.reconstitute(UUID.randomUUID(), ORGANIZATION_ID, null, adminRole,
                "Root", "Admin", "admin@optiplant.local", PASSWORD_HASH,
                true, null, Instant.now(), Instant.now());

        // Act
        User result = mapper.toDomain(mapper.toEntity(admin));

        // Assert
        assertThat(result.getBranchId()).isNull();
        assertThat(result.getRoleCode()).isEqualTo(RoleCode.ADMIN);
    }

    @Test
    @DisplayName("rechaza un código de rol desconocido almacenado en la base")
    void rejectsUnknownRoleCode() {
        // Arrange
        RoleJpaEntity corruptRole = RoleJpaEntity.builder()
                .id(UUID.randomUUID()).code("SUPERUSUARIO").name("Corrupto").build();

        UserJpaEntity entity = UserJpaEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(ORGANIZATION_ID)
                .branchId(BRANCH_ID)
                .role(corruptRole)
                .firstName("Ana").lastName("Torres")
                .email("ana@optiplant.co").passwordHash(PASSWORD_HASH)
                .active(true)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        // Act & Assert
        assertThatThrownBy(() -> mapper.toDomain(entity))
                .as("un rol corrupto debe saltar al leer, no cuando alguien decida permisos con él")
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Rol desconocido");
    }

    @Test
    @DisplayName("devuelve nulo ante entradas nulas")
    void handlesNulls() {
        // Arrange, Act & Assert
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toRoleDomain(null)).isNull();
        assertThat(mapper.toRoleEntity(null)).isNull();
    }
}
