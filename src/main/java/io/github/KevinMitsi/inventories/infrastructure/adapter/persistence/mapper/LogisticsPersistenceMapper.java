package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Carrier;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.CarrierJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.LogisticsRouteJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Traduce transportistas y rutas logísticas entre dominio y entidades persistentes.
 *
 * <p>No es un {@code @Mapper} de MapStruct por el mismo motivo que
 * {@link PurchasingPersistenceMapper}: cada agregado se reconstruye mediante un factory que
 * revalida invariantes.
 */
@Component
public class LogisticsPersistenceMapper {

    public CarrierJpaEntity toEntity(Carrier carrier) {
        if (carrier == null) {
            return null;
        }
        return CarrierJpaEntity.builder()
                .id(carrier.getId())
                .organizationId(carrier.getOrganizationId())
                .code(carrier.getCode())
                .name(carrier.getName())
                .phone(carrier.getPhone())
                .email(carrier.getEmail())
                .active(carrier.isActive())
                .createdAt(carrier.getCreatedAt())
                .updatedAt(carrier.getUpdatedAt())
                .build();
    }

    public Carrier toDomain(CarrierJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Carrier.reconstitute(entity.getId(), entity.getOrganizationId(), entity.getCode(), entity.getName(),
                entity.getPhone(), entity.getEmail(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public LogisticsRouteJpaEntity toEntity(LogisticsRoute route) {
        if (route == null) {
            return null;
        }
        return LogisticsRouteJpaEntity.builder()
                .id(route.getId())
                .organizationId(route.getOrganizationId())
                .originBranchId(route.getOriginBranchId())
                .destinationBranchId(route.getDestinationBranchId())
                .name(route.getName())
                .estimatedDurationMinutes(route.getEstimatedDurationMinutes())
                .estimatedCost(route.getEstimatedCost() == null ? null : route.getEstimatedCost().amount())
                .priority(route.getPriority())
                .active(route.isActive())
                .createdAt(route.getCreatedAt())
                .updatedAt(route.getUpdatedAt())
                .build();
    }

    public LogisticsRoute toDomain(LogisticsRouteJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Money estimatedCost = entity.getEstimatedCost() == null ? null : Money.of(entity.getEstimatedCost());
        return LogisticsRoute.reconstitute(entity.getId(), entity.getOrganizationId(), entity.getOriginBranchId(),
                entity.getDestinationBranchId(), entity.getName(), entity.getEstimatedDurationMinutes(),
                estimatedCost, entity.getPriority(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
