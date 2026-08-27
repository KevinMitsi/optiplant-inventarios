package io.github.KevinMitsi.inventories.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Rol del catálogo, con su identificador persistente y sus metadatos descriptivos.
 *
 * <p>Convive con {@link RoleCode} y cada uno cumple una función distinta: {@code RoleCode}
 * transporta las reglas de alcance y es lo que consultan las decisiones de autorización;
 * este objeto transporta la fila del catálogo, con el identificador que necesita la clave
 * foránea y el texto legible que se muestra en la interfaz.
 *
 * @param id          identificador persistente, referenciado por {@code app_user.role_id}
 * @param code        rol y sus reglas de alcance
 * @param name        nombre legible, por ejemplo "Gerente de sucursal"
 * @param description explicación de las responsabilidades del rol
 */
public record Role(UUID id, RoleCode code, String name, String description) {

    public Role {
        Objects.requireNonNull(id, "El identificador del rol no puede ser nulo.");
        Objects.requireNonNull(code, "El código del rol no puede ser nulo.");
        Objects.requireNonNull(name, "El nombre del rol no puede ser nulo.");
    }

    /** Atajo hacia las reglas de alcance, para no tener que descender hasta {@code code}. */
    public boolean canManageOrganization() {
        return code.canManageOrganization();
    }

    public boolean canOperateOnAnyBranch() {
        return code.canOperateOnAnyBranch();
    }

    public boolean canApproveTransfers() {
        return code.canApproveTransfers();
    }
}
