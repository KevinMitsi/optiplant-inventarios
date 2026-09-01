package io.github.KevinMitsi.inventories.application.port.in.query;

import java.util.UUID;

/** Filtros de búsqueda de categorías. Un campo nulo significa "no filtrar por esto". */
public record CategorySearchCriteria(UUID organizationId, String text, Boolean active) {

    public CategorySearchCriteria {
        if (text != null && text.isBlank()) {
            text = null;
        }
    }

    public static CategorySearchCriteria ofOrganization(UUID organizationId) {
        return new CategorySearchCriteria(organizationId, null, null);
    }
}
