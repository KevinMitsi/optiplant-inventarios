package io.github.KevinMitsi.inventories.domain.exception;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raíz de todos los fallos originados en el dominio.
 *
 * <p>Es {@code RuntimeException} de forma deliberada: los servicios de aplicación se
 * anotan con {@code @Transactional(rollbackFor = Exception.class)}, así que el rollback
 * está garantizado en cualquier caso, y obligar a declarar {@code throws} en cada puerto
 * ensuciaría las firmas sin aportar seguridad real.
 *
 * <p>Cada excepción transporta tres cosas:
 * <ul>
 *   <li>un {@link DomainErrorCode}, que la infraestructura traduce a un estado HTTP;</li>
 *   <li>un mensaje legible, apto para mostrarse al usuario final;</li>
 *   <li>un mapa de detalles con el contexto del fallo, que viaja en la respuesta de error
 *       para que el cliente pueda reaccionar sin tener que interpretar el texto.</li>
 * </ul>
 *
 * <p>Los detalles nunca deben contener credenciales, hashes ni datos sensibles: acaban
 * publicados en el cuerpo de la respuesta.
 */
public abstract class DomainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient DomainErrorCode errorCode;
    private final transient Map<String, Object> details;

    protected DomainException(DomainErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    protected DomainException(DomainErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null || details.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    protected DomainException(DomainErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public DomainErrorCode getErrorCode() {
        return errorCode;
    }

    /** Contexto del fallo. Nunca nulo; vacío si la excepción no aporta detalles. */
    public Map<String, Object> getDetails() {
        return details;
    }
}
