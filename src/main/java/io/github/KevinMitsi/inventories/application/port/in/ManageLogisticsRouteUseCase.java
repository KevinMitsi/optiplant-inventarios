package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateLogisticsRouteCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateLogisticsRouteCommand;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;

import java.util.UUID;

public interface ManageLogisticsRouteUseCase {

    LogisticsRoute createRoute(CreateLogisticsRouteCommand command);

    LogisticsRoute updateRoute(UpdateLogisticsRouteCommand command);

    LogisticsRoute deactivateRoute(UUID routeId);

    LogisticsRoute activateRoute(UUID routeId);
}
