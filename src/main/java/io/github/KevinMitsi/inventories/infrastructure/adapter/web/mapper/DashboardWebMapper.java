package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.domain.model.BranchComparison;
import io.github.KevinMitsi.inventories.domain.model.ProductRotation;
import io.github.KevinMitsi.inventories.domain.model.SalesSummary;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.DashboardDtos;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** Traduce las proyecciones del dashboard entre el dominio y el contrato HTTP. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DashboardWebMapper {

    DashboardDtos.SalesSummaryResponse toResponse(SalesSummary summary);

    DashboardDtos.ProductRotationResponse toResponse(ProductRotation rotation);

    DashboardDtos.BranchComparisonResponse toResponse(BranchComparison comparison);
}
