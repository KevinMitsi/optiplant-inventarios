package io.github.KevinMitsi.inventories.infrastructure.adapter.logging;

import io.github.KevinMitsi.inventories.application.port.in.RecordActivityLogUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.RecordActivityLogCommand;
import io.github.KevinMitsi.inventories.domain.model.ActivityLogLevel;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.AuthenticatedUser;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Manejador de {@code java.util.logging} que convierte cada registro de un caso de uso en
 * una fila de la traza de auditoría.
 *
 * <p>Es la pieza que hace innecesario repetir código de auditoría caso de uso por caso de
 * uso. Los casos de uso ya escriben {@code log.info(...)} porque describen lo que hacen;
 * este manejador aprovecha esos mismos mensajes y les añade lo que el dominio no puede
 * conocer sin dejar de ser dominio puro: quién está autenticado y con qué rol. La identidad
 * se resuelve aquí, en infraestructura, leyendo el contexto de seguridad.
 *
 * <p>Tres garantías que no son negociables en un manejador de registros:
 *
 * <ol>
 *   <li><b>Nunca rompe la operación auditada.</b> Cualquier fallo escribiendo la traza se
 *       reporta al {@link ErrorManager} y se traga. Que la auditoría tumbase una venta
 *       sería peor que perder una línea de traza.</li>
 *   <li><b>No se reentra.</b> Si el camino de escritura acabara emitiendo un registro
 *       propio, cada registro generaría otro sin fin. El testigo por hilo lo corta.</li>
 *   <li><b>No sustituye al registro en consola.</b> Los loggers conservan
 *       {@code useParentHandlers}, así que la salida habitual sigue intacta.</li>
 * </ol>
 */
@Component
public class ActivityLogHandler extends Handler {

    /** Testigo de reentrada: mientras está activo, este hilo no vuelve a auditar. */
    private static final ThreadLocal<Boolean> RECORDING = ThreadLocal.withInitial(() -> false);

    /** Nombre del logger al nombre de módulo declarado en {@code @AuditedUseCase}. */
    private final Map<String, String> moduleNames = new ConcurrentHashMap<>();

    private final ObjectProvider<RecordActivityLogUseCase> recordActivityLogUseCase;
    private final CurrentUserProvider currentUserProvider;

    public ActivityLogHandler(ObjectProvider<RecordActivityLogUseCase> recordActivityLogUseCase,
                              CurrentUserProvider currentUserProvider) {
        this.recordActivityLogUseCase = recordActivityLogUseCase;
        this.currentUserProvider = currentUserProvider;
        setLevel(Level.INFO);
    }

    /** Asocia un logger con el nombre de módulo bajo el que debe aparecer en la traza. */
    public void register(String loggerName, String moduleName) {
        moduleNames.put(loggerName, moduleName);
    }

    @Override
    public void publish(LogRecord logRecord) {
        if (logRecord == null || !isLoggable(logRecord) || Boolean.TRUE.equals(RECORDING.get())) {
            return;
        }

        ActivityLogLevel level = toActivityLogLevel(logRecord.getLevel());
        String operation = logRecord.getMessage();

        if (level == null || operation == null || operation.isBlank()) {
            return;
        }

        RECORDING.set(true);
        try {
            recordActivityLogUseCase.getObject().record(toCommand(logRecord, operation, level));
        } catch (Exception cause) {
            reportError("No se pudo registrar la traza de auditoría.", cause, ErrorManager.WRITE_FAILURE);
        } finally {
            RECORDING.remove();
        }
    }

    private RecordActivityLogCommand toCommand(LogRecord logRecord, String operation, ActivityLogLevel level) {
        Optional<AuthenticatedUser> user = currentUser();

        return new RecordActivityLogCommand(
                logRecord.getInstant(),
                user.map(AuthenticatedUser::email).orElse(null),
                user.map(AuthenticatedUser::userId).orElse(null),
                user.map(AuthenticatedUser::organizationId).orElse(null),
                user.map(authenticated -> authenticated.role().name()).orElse(null),
                moduleNameOf(logRecord),
                operation,
                level);
    }

    /**
     * Identidad de la petición en curso, si la hay.
     *
     * <p>Se protege aparte porque este manejador también se dispara fuera de una petición
     * —durante el arranque, por ejemplo— y un fallo leyendo el contexto de seguridad no
     * debe impedir que el suceso quede registrado como del sistema.
     */
    private Optional<AuthenticatedUser> currentUser() {
        try {
            return currentUserProvider.find();
        } catch (Exception cause) {
            return Optional.empty();
        }
    }

    /**
     * Nombre bajo el que agrupar el registro: el declarado en {@code @AuditedUseCase} o,
     * en su defecto, el nombre simple de la clase emisora.
     */
    private String moduleNameOf(LogRecord logRecord) {
        String loggerName = logRecord.getLoggerName();

        if (loggerName == null || loggerName.isBlank()) {
            return null;
        }

        String registered = moduleNames.get(loggerName);
        if (registered != null) {
            return registered;
        }

        int lastDot = loggerName.lastIndexOf('.');
        return lastDot < 0 ? loggerName : loggerName.substring(lastDot + 1);
    }

    /**
     * Reduce los siete niveles de {@code java.util.logging} a los tres del negocio.
     *
     * <p>Devuelve nulo por debajo de {@code INFO}: la depuración no pertenece a una tabla
     * de auditoría, y guardarla la llenaría de ruido que además hay que pagar en disco.
     */
    private static ActivityLogLevel toActivityLogLevel(Level level) {
        if (level == null) {
            return null;
        }
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return ActivityLogLevel.SEVERE;
        }
        if (level.intValue() >= Level.WARNING.intValue()) {
            return ActivityLogLevel.WARNING;
        }
        if (level.intValue() >= Level.INFO.intValue()) {
            return ActivityLogLevel.INFO;
        }
        return null;
    }

    @Override
    public void flush() {
        // Cada registro se escribe en su propia transacción: no hay nada pendiente que vaciar.
    }

    @Override
    public void close() {
        moduleNames.clear();
    }
}
