package io.github.KevinMitsi.inventories.infrastructure.adapter.logging;

import io.github.KevinMitsi.inventories.application.port.in.RecordActivityLogUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.RecordActivityLogCommand;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.ActivityLogLevel;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.AuthenticatedUser;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ActivityLogHandler")
class ActivityLogHandlerTest {

    private static final String LOGGER_NAME = "io.github.KevinMitsi.inventories.domain.usecase.CategoryUseCase";

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private ObjectProvider<RecordActivityLogUseCase> useCaseProvider;
    @Mock
    private RecordActivityLogUseCase recordActivityLogUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private ActivityLogHandler handler;

    @BeforeEach
    void setUp() {
        when(useCaseProvider.getObject()).thenReturn(recordActivityLogUseCase);
        when(currentUserProvider.find()).thenReturn(Optional.empty());
        handler = new ActivityLogHandler(useCaseProvider, currentUserProvider);
    }

    private LogRecord logRecord(Level level, String message) {
        LogRecord logRecord = new LogRecord(level, message);
        logRecord.setLoggerName(LOGGER_NAME);
        return logRecord;
    }

    private RecordActivityLogCommand captureCommand() {
        ArgumentCaptor<RecordActivityLogCommand> captor =
                ArgumentCaptor.forClass(RecordActivityLogCommand.class);
        verify(recordActivityLogUseCase).record(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("convierte un registro en una entrada con el usuario autenticado")
    void mapsAuthenticatedUser() {
        when(currentUserProvider.find()).thenReturn(Optional.of(new AuthenticatedUser(
                USER_ID, ORGANIZATION_ID, BRANCH_ID, RoleCode.BRANCH_MANAGER, "gerente@test.co")));

        handler.publish(logRecord(Level.INFO, "Categoría creada: id=1, código=BEB"));

        RecordActivityLogCommand command = captureCommand();
        assertThat(command.username()).isEqualTo("gerente@test.co");
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(command.role()).isEqualTo("BRANCH_MANAGER");
        assertThat(command.useCase()).isEqualTo("CategoryUseCase");
        assertThat(command.operation()).isEqualTo("Categoría creada: id=1, código=BEB");
        assertThat(command.level()).isEqualTo(ActivityLogLevel.INFO);
        assertThat(command.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("sin petición autenticada deja el autor sin resolver, para que lo archive el caso de uso")
    void leavesAuthorUnresolvedWithoutAuthentication() {
        handler.publish(logRecord(Level.INFO, "Administrador inicial creado"));

        RecordActivityLogCommand command = captureCommand();
        assertThat(command.username()).isNull();
        assertThat(command.userId()).isNull();
        assertThat(command.role()).isNull();
    }

    @Test
    @DisplayName("usa el nombre de módulo registrado para el logger")
    void usesRegisteredModuleName() {
        handler.register(LOGGER_NAME, "Catálogo");

        handler.publish(logRecord(Level.INFO, "Categoría creada"));

        assertThat(captureCommand().useCase()).isEqualTo("Catálogo");
    }

    @Test
    @DisplayName("traduce los niveles de java.util.logging a los tres del negocio")
    void mapsLevels() {
        handler.publish(logRecord(Level.WARNING, "Stock por debajo del mínimo"));
        assertThat(captureCommand().level()).isEqualTo(ActivityLogLevel.WARNING);
    }

    @Test
    @DisplayName("descarta lo que está por debajo de INFO: la depuración no es auditoría")
    void ignoresBelowInfo() {
        handler.publish(logRecord(Level.FINE, "Detalle de depuración"));
        handler.publish(logRecord(Level.INFO, "   "));
        handler.publish(logRecord(Level.INFO, null));
        handler.publish(null);

        verifyNoInteractions(recordActivityLogUseCase);
    }

    @Test
    @DisplayName("un fallo al escribir la traza no rompe la operación auditada")
    void swallowsWriteFailures() {
        doThrow(new IllegalStateException("base de datos caída"))
                .when(recordActivityLogUseCase).record(any());

        assertThatCode(() -> handler.publish(logRecord(Level.INFO, "Venta confirmada")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no se reentra: escribir la traza no genera otra traza")
    void doesNotRecurse() {
        doAnswer(invocation -> {
            // Simula que el camino de escritura acaba emitiendo su propio registro.
            handler.publish(logRecord(Level.INFO, "Traza escrita"));
            return ActivityLog.ofSystem(Instant.now(), "ActivityLogUseCase", "Traza escrita",
                    ActivityLogLevel.INFO);
        }).when(recordActivityLogUseCase).record(any());

        handler.publish(logRecord(Level.INFO, "Categoría creada"));

        verify(recordActivityLogUseCase, times(1)).record(any());
    }

    @Test
    @DisplayName("un fallo leyendo el contexto de seguridad no impide registrar")
    void survivesSecurityContextFailure() {
        when(currentUserProvider.find()).thenThrow(new IllegalStateException("contexto roto"));

        handler.publish(logRecord(Level.SEVERE, "Fallo al confirmar la venta"));

        RecordActivityLogCommand command = captureCommand();
        assertThat(command.username()).isNull();
        assertThat(command.level()).isEqualTo(ActivityLogLevel.SEVERE);
    }
}
