package io.github.KevinMitsi.inventories.application.port.in.query;

import java.util.UUID;

public record LogisticsRouteSearchCriteria(UUID organizationId, UUID originBranchId, UUID destinationBranchId,
                                           Boolean active) {

    public static LogisticsRouteSearchCriteria ofOrganization(UUID organizationId) {
        return new LogisticsRouteSearchCriteria(organizationId, null, null, null);
    }
}
