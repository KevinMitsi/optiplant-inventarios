package io.github.KevinMitsi.inventories.application.port.in.command;

import java.util.UUID;

/**
 * Resuelve una incidencia de transferencia (HU-33). No ejecuta la resolución, solo la
 * registra. Lleva {@code transferId} para que, si era la última incidencia pendiente, se
 * pueda cerrar esa transferencia sin una consulta adicional para ubicarla.
 */
public record ResolveTransferIssueCommand(UUID transferId, UUID issueId, UUID resolvedBy, String resolutionType) {
}
