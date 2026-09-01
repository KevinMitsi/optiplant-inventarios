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
 * Traduce entre {@link User} y su entidad persistente.
 *
 * <p>{@link RoleCode#fromString} rechaza cualquier código desconocido, de modo que un rol
 * corrupto o manipulado en la base se detecta al leerlo y no cuando alguien decida permisos
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
