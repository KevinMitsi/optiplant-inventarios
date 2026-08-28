package io.github.KevinMitsi.inventories.application.port.in.query;

import java.util.UUID;

public record CarrierSearchCriteria(UUID organizationId, String text, Boolean active) {

    public static CarrierSearchCriteria ofOrganization(UUID organizationId) {
        return new CarrierSearchCriteria(organizationId, null, null);
    }
}
