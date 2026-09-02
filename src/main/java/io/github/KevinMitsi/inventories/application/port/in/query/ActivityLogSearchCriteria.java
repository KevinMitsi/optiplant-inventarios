package io.github.KevinMitsi.inventories.application.port.in.query;

import java.time.Instant;
import java.util.UUID;

/**
 * Filtros de búsqueda de la traza de auditoría. Un campo nulo significa "no filtrar por esto".
 *
 * @param organizationId  organización cuya traza se consulta; nunca nulo, acota el resultado
 * @param username        correo exacto del autor
 * @param role            rol con el que actuó
 * @param useCase         caso de uso emisor
 * @param level           severidad
 * @param text            búsqueda parcial sobre la descripción de la operación
 * @param from            límite inferior de fecha, inclusive
 * @param to              límite superior de fecha, inclusive
 * @param includeSystem   añade al resultado los registros sin organización, los que emite el
 *                        propio sistema (arranque, tareas internas)
 */
public record ActivityLogSearchCriteria(UUID organizationId,
                                        String username,
                                        String role,
                                        String useCase,
                                        String level,
                                        String text,
                                        Instant from,
                                        Instant to,
                                        boolean includeSystem) {

    public ActivityLogSearchCriteria {
        username = blankToNull(username);
        role = blankToNull(role);
        useCase = blankToNull(useCase);
        level = blankToNull(level);
        text = blankToNull(text);
    }

    public static ActivityLogSearchCriteria ofOrganization(UUID organizationId) {
        return new ActivityLogSearchCriteria(organizationId, null, null, null, null, null, null, null, false);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
