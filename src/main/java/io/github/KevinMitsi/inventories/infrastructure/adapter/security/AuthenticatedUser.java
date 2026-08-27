package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import io.github.KevinMitsi.inventories.application.port.out.TokenClaims;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;

import java.util.UUID;

/**
 * Identidad de quien realiza la petición actual.
 *
 * <p>Es lo que se deposita como sujeto en el contexto de seguridad tras verificar el token,
 * y lo que los controladores consultan para saber en nombre de quién actúan.
 *
 * <p>No contiene el hash de la contraseña ni ningún dato sensible: solo lo necesario para
 * resolver la autorización y para dejar constancia de quién provocó cada movimiento (RN-11).
 *
 * @param userId         usuario autenticado
 * @param organizationId organización a la que pertenece
 * @param branchId       sucursal asignada; nulo para el administrador general
 * @param role           rol vigente al emitirse el token
 * @param email          correo, útil en trazas y auditoría
 */
public record AuthenticatedUser(UUID userId,
                                UUID organizationId,
                                UUID branchId,
                                RoleCode role,
                                String email) {

    public static AuthenticatedUser from(TokenClaims claims) {
        return new AuthenticatedUser(
                claims.userId(),
                claims.organizationId(),
                claims.branchId(),
                claims.role(),
                claims.email());
    }

    /**
     * Indica si puede realizar operaciones de escritura sobre una sucursal.
     *
     * <p>Replica la regla de {@code User.canOperateOnBranch} para poder decidir sin cargar
     * el usuario de la base. Es la comprobación que las anotaciones de método no alcanzan,
     * porque exige comparar la sucursal del recurso con la del solicitante y eso solo se
     * sabe una vez cargado el recurso (RN-12, RN-13).
     */
    public boolean canOperateOnBranch(UUID targetBranchId) {
        if (role.canOperateOnAnyBranch()) {
            return true;
        }
        return branchId != null && branchId.equals(targetBranchId);
    }

    /**
     * Indica si pertenece a una organización.
     *
     * <p>Toda consulta se acota a la organización del solicitante: sin esta comprobación, un
     * identificador de organización ajeno en la ruta daría acceso a datos de otra empresa.
     */
    public boolean belongsToOrganization(UUID targetOrganizationId) {
        return organizationId.equals(targetOrganizationId);
    }

    public boolean isAdmin() {
        return role == RoleCode.ADMIN;
    }
}
