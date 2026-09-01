package io.github.KevinMitsi.inventories.application.port.in.query;

import java.util.UUID;

/**
 * Filtros de búsqueda de sucursales.
 *
 * <p>Agrupar los criterios en un objeto, en lugar de encadenar parámetros sueltos en la
 * firma del puerto, evita el problema clásico de varios argumentos del mismo tipo que se
 * pueden intercambiar sin que el compilador avise, y permite añadir un filtro nuevo sin
 * romper a quien ya implementa la interfaz.
 *
 * <p>Un campo nulo significa "no filtrar por esto".
 *
 * @param organizationId organización cuyas sucursales se consultan; obligatorio, porque
 *                       ninguna consulta debe poder cruzar la frontera de la organización
 * @param text           búsqueda parcial e insensible a mayúsculas sobre código y nombre
 * @param city           ciudad exacta
 * @param active         estado de alta; nulo devuelve activas e inactivas
 */
public record BranchSearchCriteria(UUID organizationId, String text, String city, Boolean active) {

    public BranchSearchCriteria {
        if (text != null && text.isBlank()) {
            text = null;
        }
        if (city != null && city.isBlank()) {
            city = null;
        }
    }

    /** Todas las sucursales de la organización, sin filtros adicionales. */
    public static BranchSearchCriteria ofOrganization(UUID organizationId) {
        return new BranchSearchCriteria(organizationId, null, null, null);
    }

    /** Solo las sucursales operativas de la organización. */
    public static BranchSearchCriteria activeOfOrganization(UUID organizationId) {
        return new BranchSearchCriteria(organizationId, null, null, true);
    }
}
