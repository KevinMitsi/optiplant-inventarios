package io.github.KevinMitsi.inventories.application.port.in.query;

import java.util.Locale;
import java.util.UUID;

/**
 * Filtros de búsqueda de productos. Un campo nulo significa "no filtrar por esto".
 *
 * @param text  búsqueda parcial e insensible a mayúsculas sobre SKU, nombre y código de barras
 * @param scope qué parte de la familia devolver. Es un enum y no dos banderas sueltas
 *              ({@code soloPrincipales} + {@code soloVariantes}) porque así no existe la
 *              combinación contradictoria.
 */
public record ProductSearchCriteria(UUID organizationId,
                                    UUID categoryId,
                                    String text,
                                    Boolean active,
                                    VariantScope scope) {

    /** Alcance de una búsqueda respecto a la relación principal/variante. */
    public enum VariantScope {

        /** Principales y variantes por igual: todo lo que se puede vender e inventariar. */
        ALL,

        /** Solo los productos que encabezan una familia. Vista agrupada del catálogo. */
        PRINCIPALS_ONLY,

        /** Solo variantes. */
        VARIANTS_ONLY;

        public static VariantScope fromString(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            return VariantScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public ProductSearchCriteria {
        if (text != null && text.isBlank()) {
            text = null;
        }
        scope = scope == null ? VariantScope.ALL : scope;
    }

    public static ProductSearchCriteria ofOrganization(UUID organizationId) {
        return new ProductSearchCriteria(organizationId, null, null, null, VariantScope.ALL);
    }
}
