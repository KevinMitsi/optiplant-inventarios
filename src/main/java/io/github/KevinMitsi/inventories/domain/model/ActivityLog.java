package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrada de la traza de auditoría: qué se hizo, cuándo, quién y con qué rol.
 *
 * <p>Es inmutable y de solo inserción, como el propio {@code inventory_movement}: una traza
 * que se puede editar no prueba nada (RNF-12). Por eso es un {@code record} y no una entidad
 * con ciclo de vida, y por eso no existe ninguna operación de actualización o borrado en su
 * puerto de salida.
 *
 * <p>Los datos del autor se copian aquí en lugar de resolverse por clave foránea contra
 * {@code app_user}. Es denormalización deliberada: el registro debe seguir diciendo quién
 * hizo qué aunque el usuario cambie de correo, cambie de rol o se dé de baja. Una traza que
 * se reescribe sola cuando cambia otra tabla no sirve como evidencia.
 *
 * @param id             identificador único de la entrada
 * @param occurredAt     instante en que se produjo, en UTC
 * @param username       correo del usuario que la provocó, o {@link #SYSTEM_USERNAME}
 * @param userId         usuario que la provocó; nulo cuando la origina el propio sistema
 * @param organizationId organización en cuyo contexto ocurrió; nula para sucesos del sistema
 * @param role           rol vigente del usuario, o {@link #SYSTEM_ROLE}
 * @param useCase        caso de uso que emitió el registro
 * @param operation      descripción de la operación
 * @param level          severidad
 */
public record ActivityLog(UUID id,
                          Instant occurredAt,
                          String username,
                          UUID userId,
                          UUID organizationId,
                          String role,
                          String useCase,
                          String operation,
                          ActivityLogLevel level) {

    /** Autor de los registros emitidos sin petición autenticada detrás (arranque, tareas). */
    public static final String SYSTEM_USERNAME = "sistema";

    /** Rol con el que se archivan esos mismos registros. */
    public static final String SYSTEM_ROLE = "SYSTEM";

    public static final int USERNAME_MAX_LENGTH = 150;
    public static final int ROLE_MAX_LENGTH = 30;
    public static final int USE_CASE_MAX_LENGTH = 150;
    public static final int OPERATION_MAX_LENGTH = 1000;

    public ActivityLog {
        if (id == null) {
            throw new DomainValidationException("id", "El identificador del registro es obligatorio.");
        }
        if (occurredAt == null) {
            throw new DomainValidationException("occurredAt", "La fecha del registro es obligatoria.");
        }
        if (level == null) {
            throw new DomainValidationException("level", "El nivel del registro es obligatorio.");
        }
        username = requireText(username, "username", "El usuario del registro es obligatorio.",
                USERNAME_MAX_LENGTH);
        role = requireText(role, "role", "El rol del registro es obligatorio.", ROLE_MAX_LENGTH);
        useCase = requireText(useCase, "useCase", "El caso de uso del registro es obligatorio.",
                USE_CASE_MAX_LENGTH);
        operation = requireText(operation, "operation", "La operación del registro es obligatoria.",
                OPERATION_MAX_LENGTH);
    }

    /** Registro de una operación realizada por un usuario identificado. */
    public static ActivityLog of(Instant occurredAt,
                                 String username,
                                 UUID userId,
                                 UUID organizationId,
                                 String role,
                                 String useCase,
                                 String operation,
                                 ActivityLogLevel level) {
        return new ActivityLog(UUID.randomUUID(), occurredAt, username, userId, organizationId,
                role, useCase, operation, level);
    }

    /** Registro sin usuario autenticado detrás: arranque de la aplicación, tareas internas. */
    public static ActivityLog ofSystem(Instant occurredAt,
                                       String useCase,
                                       String operation,
                                       ActivityLogLevel level) {
        return of(occurredAt, SYSTEM_USERNAME, null, null, SYSTEM_ROLE, useCase, operation, level);
    }

    public boolean isSystemGenerated() {
        return userId == null;
    }

    /**
     * Recorta un texto al máximo admitido por la columna.
     *
     * <p>El mensaje lo compone el caso de uso y puede incluir datos de longitud
     * imprevisible. Recortar es preferible a fallar: la alternativa sería que un mensaje
     * largo tumbase la operación de negocio que estaba registrando.
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String requireText(String value, String field, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainValidationException(field,
                    "El campo '%s' no puede superar %d caracteres.".formatted(field, maxLength));
        }
        return normalized;
    }
}
