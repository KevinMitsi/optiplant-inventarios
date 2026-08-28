package io.github.KevinMitsi.inventories.application.port.in.command;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateLogisticsRouteCommand(UUID routeId, String name, int estimatedDurationMinutes,
                                          BigDecimal estimatedCost, Short priority) {
}
