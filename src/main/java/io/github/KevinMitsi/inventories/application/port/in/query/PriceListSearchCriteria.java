package io.github.KevinMitsi.inventories.application.port.in.query;

import java.util.UUID;

public record PriceListSearchCriteria(UUID organizationId, Boolean active) {

    public static PriceListSearchCriteria ofOrganization(UUID organizationId) {
        return new PriceListSearchCriteria(organizationId, null);
    }
}
