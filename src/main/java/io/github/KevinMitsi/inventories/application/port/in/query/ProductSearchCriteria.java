package io.github.KevinMitsi.inventories.application.port.in.query;

import java.util.UUID;

/**
 * Filtros de búsqueda de productos. Un campo nulo significa "no filtrar por esto".
 *
 * @param text búsqueda parcial e insensible a mayúsculas sobre SKU, nombre y código de barras
 */
public record ProductSearchCriteria(UUID organizationId,
                                    UUID categoryId,
                                    String text,
                                    Boolean active) {

    public ProductSearchCriteria {
        if (text != null && text.isBlank()) {
            text = null;
        }
    }

    public static ProductSearchCriteria ofOrganization(UUID organizationId) {
        return new ProductSearchCriteria(organizationId, null, null, null);
    }
}
