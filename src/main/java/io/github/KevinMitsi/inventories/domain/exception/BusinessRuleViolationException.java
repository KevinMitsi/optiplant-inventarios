package io.github.KevinMitsi.inventories.domain.exception;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Incumplimiento de una regla de negocio del catálogo RN-01..RN-13.
 *
 * <p>El identificador de la regla viaja dentro de la excepción y llega hasta la respuesta
 * de error. Eso permite que la trazabilidad funcione en los dos sentidos: desde un fallo
 * observado en producción se llega al requisito que lo define, y desde el requisito se
 * puede buscar dónde se aplica en el código.
 */
public class BusinessRuleViolationException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param ruleId  identificador de la regla, por ejemplo {@code "RN-07"}
     * @param message explicación en lenguaje natural de por qué se rechaza la operación
     */
    public BusinessRuleViolationException(String ruleId, String message) {
        super(DomainErrorCode.BUSINESS_RULE_VIOLATION, message, Map.of("rule", ruleId));
    }

    public BusinessRuleViolationException(String ruleId, String message, Map<String, Object> details) {
        super(DomainErrorCode.BUSINESS_RULE_VIOLATION, message, withRule(ruleId, details));
    }

    private static Map<String, Object> withRule(String ruleId, Map<String, Object> details) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("rule", ruleId);
        if (details != null) {
            merged.putAll(details);
        }
        return merged;
    }
}
