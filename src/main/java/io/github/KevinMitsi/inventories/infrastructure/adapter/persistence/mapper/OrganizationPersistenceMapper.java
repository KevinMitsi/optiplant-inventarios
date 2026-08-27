package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.OrganizationJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Traduce entre {@link Organization} y su entidad persistente. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrganizationPersistenceMapper {

    OrganizationJpaEntity toEntity(Organization organization);

    default Organization toDomain(OrganizationJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Organization.reconstitute(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getLegalName(),
                entity.getTaxId(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
