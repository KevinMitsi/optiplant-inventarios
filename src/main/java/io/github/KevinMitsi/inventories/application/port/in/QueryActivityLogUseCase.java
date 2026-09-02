package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.ActivityLogSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;

/** Consulta de la traza de auditoría. */
public interface QueryActivityLogUseCase {

    PageResult<ActivityLog> searchActivityLogs(ActivityLogSearchCriteria criteria, PageQuery pageQuery);

    ActivityLog getActivityLogById(UUID activityLogId);
}
