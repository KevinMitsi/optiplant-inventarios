package io.github.KevinMitsi.inventories.application.port.in.query;

import io.github.KevinMitsi.inventories.domain.model.RoleCode;

import java.util.UUID;

/**
 * Filtros de búsqueda de usuarios. Un campo nulo significa "no filtrar por esto".
 *
 * @param organizationId organización cuyos usuarios se consultan; obligatorio, para que
 *                       ninguna consulta cruce la frontera de la organización
 * @param branchId       sucursal asignada
 * @param role           rol
 * @param text           búsqueda parcial e insensible a mayúsculas sobre nombre y correo
 * @param active         estado de la cuenta; nulo devuelve activas e inactivas
 */
public record UserSearchCriteria(UUID organizationId,
                                 UUID branchId,
                                 RoleCode role,
                                 String text,
                                 Boolean active) {

    public UserSearchCriteria {
        if (text != null && text.isBlank()) {
            text = null;
        }
    }

    public static UserSearchCriteria ofOrganization(UUID organizationId) {
        return new UserSearchCriteria(organizationId, null, null, null, null);
    }

    public static UserSearchCriteria ofBranch(UUID organizationId, UUID branchId) {
        return new UserSearchCriteria(organizationId, branchId, null, null, null);
    }
}
