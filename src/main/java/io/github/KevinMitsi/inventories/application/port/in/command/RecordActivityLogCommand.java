package io.github.KevinMitsi.inventories.application.port.in.command;

import io.github.KevinMitsi.inventories.domain.model.ActivityLogLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Datos de una entrada de traza tal como los recoge el manejador de registros.
 *
 * @param occurredAt     instante del registro; si es nulo se toma el actual
 * @param username       correo del autor; nulo cuando no hay petición autenticada
 * @param userId         autor; nulo cuando no hay petición autenticada
 * @param organizationId organización del autor; nula cuando no hay petición autenticada
 * @param role           rol del autor; nulo cuando no hay petición autenticada
 * @param useCase        caso de uso emisor
 * @param operation      descripción de la operación
 * @param level          severidad
 */
public record RecordActivityLogCommand(Instant occurredAt,
                                       String username,
                                       UUID userId,
                                       UUID organizationId,
                                       String role,
                                       String useCase,
                                       String operation,
                                       ActivityLogLevel level) {
}
