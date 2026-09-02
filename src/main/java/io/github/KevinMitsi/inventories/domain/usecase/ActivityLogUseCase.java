package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.QueryActivityLogUseCase;
import io.github.KevinMitsi.inventories.application.port.in.RecordActivityLogUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.RecordActivityLogCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.ActivityLogSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.ActivityLogRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.ActivityLogLevel;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.time.Instant;
import java.util.UUID;

// Este caso de uso no lleva @AuditedUseCase ni Logger: registrar aquí provocaría un nuevo
// registro por cada registro escrito, y la recursión no terminaría nunca.
public class ActivityLogUseCase implements RecordActivityLogUseCase, QueryActivityLogUseCase {

    private static final String ACTIVITY_LOG = "el registro de auditoría";
    private static final String ORGANIZATION = "la organización";
    private static final String UNKNOWN_USE_CASE = "desconocido";

    private final ActivityLogRepositoryPort activityLogRepository;
    private final OrganizationRepositoryPort organizationRepository;

    public ActivityLogUseCase(ActivityLogRepositoryPort activityLogRepository,
                              OrganizationRepositoryPort organizationRepository) {
        this.activityLogRepository = activityLogRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public ActivityLog record(RecordActivityLogCommand command) {
        Instant occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        ActivityLogLevel level = command.level() == null ? ActivityLogLevel.INFO : command.level();

        String useCase = ActivityLog.truncate(
                blankToDefault(command.useCase(), UNKNOWN_USE_CASE), ActivityLog.USE_CASE_MAX_LENGTH);
        String operation = ActivityLog.truncate(command.operation(), ActivityLog.OPERATION_MAX_LENGTH);

        // Sin usuario en el contexto la traza sigue escribiéndose: un suceso sin autor
        // conocido es información, y descartarlo dejaría huecos justo en el arranque y en
        // las tareas internas, que es donde más cuesta reconstruir lo ocurrido.
        String username = ActivityLog.truncate(
                blankToDefault(command.username(), ActivityLog.SYSTEM_USERNAME),
                ActivityLog.USERNAME_MAX_LENGTH);
        String role = ActivityLog.truncate(
                blankToDefault(command.role(), ActivityLog.SYSTEM_ROLE), ActivityLog.ROLE_MAX_LENGTH);

        return activityLogRepository.save(ActivityLog.of(occurredAt, username, command.userId(),
                command.organizationId(), role, useCase, operation, level));
    }

    @Override
    public PageResult<ActivityLog> searchActivityLogs(ActivityLogSearchCriteria criteria, PageQuery pageQuery) {
        if (!organizationRepository.existsById(criteria.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, criteria.organizationId());
        }

        if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
            throw new DomainValidationException("from",
                    "La fecha inicial no puede ser posterior a la final.");
        }

        return activityLogRepository.search(criteria, pageQuery);
    }

    @Override
    public ActivityLog getActivityLogById(UUID activityLogId) {
        return activityLogRepository.findById(activityLogId)
                .orElseThrow(() -> new ResourceNotFoundException(ACTIVITY_LOG, activityLogId));
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
