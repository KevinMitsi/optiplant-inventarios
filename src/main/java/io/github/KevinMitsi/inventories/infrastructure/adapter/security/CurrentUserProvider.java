package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import io.github.KevinMitsi.inventories.application.exception.OperationNotPermittedException;
import io.github.KevinMitsi.inventories.domain.exception.DomainErrorCode;
import io.github.KevinMitsi.inventories.domain.exception.DomainException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a la identidad de quien realiza la petición actual.
 *
 * <p>Concentra la lectura del contexto de seguridad en un solo sitio. Sin esto, cada
 * controlador repetiría el descenso por {@code SecurityContextHolder} con sus comprobaciones
 * de nulidad y sus conversiones de tipo, y bastaría con que uno lo hiciera mal para abrir un
 * agujero de autorización.
 *
 * <p>Vive en el adaptador de seguridad, no en la capa de aplicación: los servicios reciben
 * los identificadores que necesitan como argumentos explícitos, en lugar de ir a buscarlos a
 * un contexto implícito. Eso los mantiene comprobables sin montar un contexto de seguridad y
 * deja a la vista, en la firma de cada método, de qué depende realmente.
 */
@Component
public class CurrentUserProvider {

    /**
     * Devuelve la identidad actual, o vacío si la petición es anónima.
     *
     * <p>Lo usan los endpoints públicos que se comportan de forma distinta según haya o no
     * sesión, sin llegar a exigirla.
     */
    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }

        return Optional.of(user);
    }

    /**
     * Devuelve la identidad actual y falla si no la hay.
     *
     * <p>Es el método que usan los endpoints privados. Que falle en lugar de devolver nulo
     * evita el escenario peligroso: una comprobación de permisos que recibe un nulo, lo
     * interpreta como "sin restricciones" y deja pasar la operación.
     */
    public AuthenticatedUser require() {
        return find().orElseThrow(NotAuthenticatedException::new);
    }

    /** Identificador del usuario actual, para registrarlo como responsable (RN-11). */
    public UUID requireUserId() {
        return require().userId();
    }

    /**
     * Exige que el usuario actual pueda operar sobre una sucursal (RN-12, RN-13).
     *
     * @throws OperationNotPermittedException si la sucursal no está dentro de su alcance
     */
    public AuthenticatedUser requireCanOperateOnBranch(UUID branchId, String operation) {
        AuthenticatedUser user = require();

        if (!user.canOperateOnBranch(branchId)) {
            throw new OperationNotPermittedException(operation,
                    "la operación afecta a una sucursal distinta de la asignada al usuario");
        }

        return user;
    }

    /**
     * Exige que el usuario actual pertenezca a la organización indicada.
     *
     * <p>Sin esta comprobación, cambiar el identificador de organización en la ruta daría
     * acceso a los datos de otra empresa.
     */
    public AuthenticatedUser requireBelongsToOrganization(UUID organizationId, String operation) {
        AuthenticatedUser user = require();

        if (!user.belongsToOrganization(organizationId)) {
            throw new OperationNotPermittedException(operation,
                    "la operación afecta a una organización distinta de la del usuario");
        }

        return user;
    }

    /** No hay identidad establecida para la petición actual. Se traduce a 401. */
    public static class NotAuthenticatedException extends DomainException {

        @Serial
        private static final long serialVersionUID = 1L;

        public NotAuthenticatedException() {
            super(DomainErrorCode.AUTHENTICATION_FAILED,
                    "Esta operación requiere autenticación.");
        }
    }
}
