package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.ActivityLogLevel;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ActivityLogJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Traduce la traza de auditoría entre modelo de dominio y entidad persistente. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ActivityLogPersistenceMapper {

    ActivityLogJpaEntity toEntity(ActivityLog activityLog);

    default ActivityLog toDomain(ActivityLogJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ActivityLog(
                entity.getId(),
                entity.getOccurredAt(),
                entity.getUsername(),
                entity.getUserId(),
                entity.getOrganizationId(),
                entity.getRole(),
                entity.getUseCase(),
                entity.getOperation(),
                ActivityLogLevel.fromString(entity.getLevel()));
    }
}
