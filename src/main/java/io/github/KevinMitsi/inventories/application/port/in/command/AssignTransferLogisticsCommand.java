package io.github.KevinMitsi.inventories.application.port.in.command;

import java.time.Instant;
import java.util.UUID;

/**
 * Asigna transportista y ruta a una transferencia (Fase 5, Logística) — solo válido antes de
 * despachar (ver {@code Transfer.assignLogistics}).
 */
public record AssignTransferLogisticsCommand(UUID transferId, UUID carrierId, UUID routeId,
                                             Instant estimatedArrivalAt) {
}
