package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.RoleCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Contenido de un token ya verificado.
 *
 * <p>Transporta lo justo para resolver la autorización sin volver a consultar la base de
 * datos en cada petición: quién es, de qué organización, con qué rol y sobre qué sucursal.
 * Ese es el motivo de elegir un token con estado propio frente a una sesión en servidor —
 * cada petición se valida por sí sola y las instancias no necesitan compartir estado
 * (RNF-08).
 *
 * <p>La contrapartida es que estos datos son una foto del momento en que se emitió el
 * token: si a un usuario se le cambia el rol o se le da de baja, su token sigue siendo
 * criptográficamente válido hasta que caduque. Por eso el tiempo de vida del token de
 * acceso es corto (1 hora) y las operaciones sensibles vuelven a cargar el usuario en lugar
 * de fiarse solo de lo que dice el token.
 *
 * @param userId         sujeto del token
 * @param organizationId organización a la que pertenece
 * @param branchId       sucursal asignada; nulo para el administrador general
 * @param role           rol vigente al emitirse el token
 * @param email          correo del usuario, útil para trazas
 * @param issuedAt       instante de emisión
 * @param expiresAt      instante de caducidad
 * @param refreshToken   indica si es un token de renovación, que no autoriza operaciones
 */
public record TokenClaims(UUID userId,
                          UUID organizationId,
                          UUID branchId,
                          RoleCode role,
                          String email,
                          Instant issuedAt,
                          Instant expiresAt,
                          boolean refreshToken) {

    /** Indica si es un token de acceso, es decir, si sirve para autorizar operaciones. */
    public boolean isAccessToken() {
        return !refreshToken;
    }
}
