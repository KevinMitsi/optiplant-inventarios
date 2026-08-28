package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateCarrierCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateLogisticsRouteCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateCarrierCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateLogisticsRouteCommand;
import io.github.KevinMitsi.inventories.domain.model.Carrier;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.RouteComplianceSummary;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CarrierDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.LogisticsRouteDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.UUID;

/** Traduce transportistas y rutas logísticas entre el contrato HTTP y la capa de aplicación. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface LogisticsWebMapper {

    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "code", source = "request.code")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "phone", source = "request.phone")
    @Mapping(target = "email", source = "request.email")
    CreateCarrierCommand toCommand(UUID organizationId, CarrierDtos.CreateCarrierRequest request);

    @Mapping(target = "carrierId", source = "carrierId")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "phone", source = "request.phone")
    @Mapping(target = "email", source = "request.email")
    UpdateCarrierCommand toCommand(UUID carrierId, CarrierDtos.UpdateCarrierRequest request);

    CarrierDtos.CarrierResponse toResponse(Carrier carrier);

    default CreateLogisticsRouteCommand toCommand(UUID organizationId,
                                                   LogisticsRouteDtos.CreateLogisticsRouteRequest request) {
        return new CreateLogisticsRouteCommand(organizationId, request.originBranchId(),
                request.destinationBranchId(), request.name(), request.estimatedDurationMinutes(),
                request.estimatedCost(), request.priority());
    }

    default UpdateLogisticsRouteCommand toCommand(UUID routeId,
                                                   LogisticsRouteDtos.UpdateLogisticsRouteRequest request) {
        return new UpdateLogisticsRouteCommand(routeId, request.name(), request.estimatedDurationMinutes(),
                request.estimatedCost(), request.priority());
    }

    LogisticsRouteDtos.LogisticsRouteResponse toResponse(LogisticsRoute route);

    LogisticsRouteDtos.RouteComplianceResponse toResponse(RouteComplianceSummary summary);

    default BigDecimal map(Money money) {
        return money == null ? null : money.amount();
    }
}
