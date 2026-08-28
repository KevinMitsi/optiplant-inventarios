package io.github.KevinMitsi.inventories.application.port.in.query;

import java.util.UUID;

public record SupplierSearchCriteria(UUID organizationId, String text, Boolean active) {

    public static SupplierSearchCriteria ofOrganization(UUID organizationId) {
        return new SupplierSearchCriteria(organizationId, null, null);
    }
}
