package io.github.KevinMitsi.inventories.application.exception;

import io.github.KevinMitsi.inventories.domain.exception.DomainErrorCode;
import io.github.KevinMitsi.inventories.domain.exception.DomainException;

import java.io.Serial;
import java.util.Map;

/**
 * Otra transacción modificó el mismo registro mientras esta lo tenía leído.
 *
 * <p>Nace del bloqueo optimista sobre {@code inventory.version}. Dos operaciones que
 * descuentan del mismo saldo a la vez no pueden confirmarse ambas sin arriesgar un
 * stock incoherente (RNF-05), así que la segunda se rechaza y el cliente reintenta
 * sobre el saldo ya actualizado.
 *
 * <p>Se responde con 409: el fallo es transitorio y la misma petición puede tener éxito
 * si se repite, a diferencia de una regla de negocio incumplida.
 */
public class ConcurrentModificationConflictException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConcurrentModificationConflictException(String resource, Object identifier) {
        super(DomainErrorCode.CONCURRENT_MODIFICATION,
                ("El registro %s '%s' fue modificado por otra operación simultánea. "
                        + "Vuelva a consultarlo y repita la acción.").formatted(resource, identifier),
                Map.of("resource", resource, "identifier", String.valueOf(identifier), "retryable", true));
    }

    public ConcurrentModificationConflictException(String resource, Object identifier, Throwable cause) {
        super(DomainErrorCode.CONCURRENT_MODIFICATION,
                ("El registro %s '%s' fue modificado por otra operación simultánea. "
                        + "Vuelva a consultarlo y repita la acción.").formatted(resource, identifier),
                cause);
    }
}
