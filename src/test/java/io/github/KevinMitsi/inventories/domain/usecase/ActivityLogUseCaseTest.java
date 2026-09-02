package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.RecordActivityLogCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.ActivityLogSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.ActivityLogRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.ActivityLogLevel;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ActivityLogUseCase")
class ActivityLogUseCaseTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ActivityLogRepositoryPort activityLogRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;

    private ActivityLogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ActivityLogUseCase(activityLogRepository, organizationRepository);
        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(activityLogRepository.save(any(ActivityLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ActivityLog captureSaved() {
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("record")
    class Record {

        @Test
        @DisplayName("guarda la entrada con el autor y el rol recibidos")
        void savesEntryWithAuthor() {
            Instant occurredAt = Instant.parse("2026-09-02T10:15:30Z");

            useCase.record(new RecordActivityLogCommand(occurredAt, "gerente@test.co", USER_ID,
                    ORGANIZATION_ID, "BRANCH_MANAGER", "CategoryUseCase", "Categoría creada",
                    ActivityLogLevel.INFO));

            ActivityLog saved = captureSaved();
            assertThat(saved.occurredAt()).isEqualTo(occurredAt);
            assertThat(saved.username()).isEqualTo("gerente@test.co");
            assertThat(saved.userId()).isEqualTo(USER_ID);
            assertThat(saved.organizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(saved.role()).isEqualTo("BRANCH_MANAGER");
            assertThat(saved.useCase()).isEqualTo("CategoryUseCase");
            assertThat(saved.operation()).isEqualTo("Categoría creada");
            assertThat(saved.level()).isEqualTo(ActivityLogLevel.INFO);
            assertThat(saved.isSystemGenerated()).isFalse();
        }

        @Test
        @DisplayName("sin autor, archiva la entrada como del sistema en lugar de descartarla")
        void fallsBackToSystemAuthor() {
            useCase.record(new RecordActivityLogCommand(null, null, null, null, null,
                    "AdminBootstrapUseCase", "Administrador inicial creado", null));

            ActivityLog saved = captureSaved();
            assertThat(saved.username()).isEqualTo(ActivityLog.SYSTEM_USERNAME);
            assertThat(saved.role()).isEqualTo(ActivityLog.SYSTEM_ROLE);
            assertThat(saved.userId()).isNull();
            assertThat(saved.organizationId()).isNull();
            assertThat(saved.level()).isEqualTo(ActivityLogLevel.INFO);
            assertThat(saved.occurredAt()).isNotNull();
            assertThat(saved.isSystemGenerated()).isTrue();
        }

        @Test
        @DisplayName("recorta la operación en lugar de fallar cuando excede la columna")
        void truncatesLongOperation() {
            String operation = "x".repeat(ActivityLog.OPERATION_MAX_LENGTH + 500);

            useCase.record(new RecordActivityLogCommand(Instant.now(), "admin@test.co", USER_ID,
                    ORGANIZATION_ID, "ADMIN", "SaleUseCase", operation, ActivityLogLevel.WARNING));

            assertThat(captureSaved().operation()).hasSize(ActivityLog.OPERATION_MAX_LENGTH);
        }

        @Test
        @DisplayName("sin caso de uso emisor, lo marca como desconocido")
        void fallsBackToUnknownUseCase() {
            useCase.record(new RecordActivityLogCommand(Instant.now(), "admin@test.co", USER_ID,
                    ORGANIZATION_ID, "ADMIN", null, "Algo ocurrió", ActivityLogLevel.SEVERE));

            assertThat(captureSaved().useCase()).isEqualTo("desconocido");
        }
    }

    @Nested
    @DisplayName("searchActivityLogs")
    class Search {

        @Test
        @DisplayName("delega la búsqueda en el repositorio")
        void delegatesSearch() {
            ActivityLogSearchCriteria criteria = ActivityLogSearchCriteria.ofOrganization(ORGANIZATION_ID);
            PageQuery pageQuery = PageQuery.firstPage();
            PageResult<ActivityLog> expected = new PageResult<>(List.of(), 0, 20, 0);
            when(activityLogRepository.search(criteria, pageQuery)).thenReturn(expected);

            assertThat(useCase.searchActivityLogs(criteria, pageQuery)).isSameAs(expected);
        }

        @Test
        @DisplayName("falla si la organización no existe")
        void rejectsUnknownOrganization() {
            UUID unknown = UUID.randomUUID();
            when(organizationRepository.existsById(unknown)).thenReturn(false);

            assertThatThrownBy(() -> useCase.searchActivityLogs(
                    ActivityLogSearchCriteria.ofOrganization(unknown), PageQuery.firstPage()))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(activityLogRepository, never()).search(any(), any());
        }

        @Test
        @DisplayName("falla si la fecha inicial es posterior a la final")
        void rejectsInvertedRange() {
            ActivityLogSearchCriteria criteria = new ActivityLogSearchCriteria(ORGANIZATION_ID,
                    null, null, null, null, null,
                    Instant.parse("2026-09-02T10:00:00Z"), Instant.parse("2026-09-01T10:00:00Z"), false);

            assertThatThrownBy(() -> useCase.searchActivityLogs(criteria, PageQuery.firstPage()))
                    .isInstanceOf(DomainValidationException.class);

            verify(activityLogRepository, never()).search(any(), any());
        }
    }

    @Nested
    @DisplayName("getActivityLogById")
    class GetById {

        @Test
        @DisplayName("devuelve la entrada existente")
        void returnsExistingEntry() {
            ActivityLog entry = ActivityLog.ofSystem(Instant.now(), "CategoryUseCase",
                    "Categoría creada", ActivityLogLevel.INFO);
            when(activityLogRepository.findById(entry.id())).thenReturn(Optional.of(entry));

            assertThat(useCase.getActivityLogById(entry.id())).isEqualTo(entry);
        }

        @Test
        @DisplayName("falla si la entrada no existe")
        void rejectsUnknownEntry() {
            UUID unknown = UUID.randomUUID();
            when(activityLogRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.getActivityLogById(unknown))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
