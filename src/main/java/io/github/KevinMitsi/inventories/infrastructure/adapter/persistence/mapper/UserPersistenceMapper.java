package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.RoleJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UserJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Traduce entre {@link User} y su entidad persistente, y entre {@link Role} y la suya.
 *
 * <p>Ambas direcciones se escriben a mano porque los tipos de dominio no tienen
 * constructores públicos ni asignadores: se construyen por sus factorías, que revalidan los
 * invariantes. Esa revalidación es deseable, y un mapeo por reflexión se la saltaría.
 *
 * <p>La conversión del rol merece atención: en la base es una cadena y en el dominio un
 * enum. {@link RoleCode#fromString} rechaza cualquier valor desconocido, de modo que un dato
 * corrupto o manipulado se detecta al leerlo y no cuando alguien intente decidir permisos
 * con él.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserPersistenceMapper {

    default UserJpaEntity toEntity(User user) {
        if (user == null) {
            return null;
        }
        return UserJpaEntity.builder()
                .id(user.getId())
                .organizationId(user.getOrganizationId())
                .branchId(user.getBranchId())
                .role(toRoleEntity(user.getRole()))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash())
                .active(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    default User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return User.reconstitute(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getBranchId(),
                toRoleDomain(entity.getRole()),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.isActive(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    default RoleJpaEntity toRoleEntity(Role role) {
        if (role == null) {
            return null;
        }
        return RoleJpaEntity.builder()
                .id(role.id())
                .code(role.code().name())
                .name(role.name())
                .description(role.description())
                .build();
    }

    default Role toRoleDomain(RoleJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Role(
                entity.getId(),
                RoleCode.fromString(entity.getCode()),
                entity.getName(),
                entity.getDescription());
    }
}
