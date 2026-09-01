package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.BranchJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Traduce entre {@link Branch} y su entidad persistente.
 *
 * <p>La dirección entidad a dominio se escribe a mano porque pasa por {@code reconstitute},
 * que revalida los invariantes: un dato corrupto en la base salta al leerlo y no varias
 * operaciones más tarde.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BranchPersistenceMapper {

    BranchJpaEntity toEntity(Branch branch);

    default Branch toDomain(BranchJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Branch.reconstitute(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getCode(),
                entity.getName(),
                entity.getAddressLine(),
                entity.getCity(),
                entity.getCountryCode(),
                entity.getPhone(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
