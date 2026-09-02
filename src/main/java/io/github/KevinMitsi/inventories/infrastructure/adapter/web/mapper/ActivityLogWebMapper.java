package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ActivityLogDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ActivityLogWebMapper {

    @Mapping(target = "systemGenerated", expression = "java(activityLog.isSystemGenerated())")
    ActivityLogDtos.ActivityLogResponse toResponse(ActivityLog activityLog);
}
