package io.github.KevinMitsi.inventories.domain.exception;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El saldo disponible no cubre la cantidad solicitada.
 *
 * <p>Cubre los dos puntos donde el sistema se niega a mover mercancía que no tiene:
 * la confirmación de una venta (RN-03) y el despacho de una transferencia (RN-08).
 *
 * <p>Se distingue de {@link BusinessRuleViolationException} porque el cliente necesita
 * reaccionar de forma específica: mostrar cuánto hay realmente, ofrecer ajustar la
 * cantidad o buscar el producto en otra sucursal. Por eso lleva las cifras exactas.
 */
public class InsufficientStockException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient BigDecimal requested;
    private final transient BigDecimal available;

    public InsufficientStockException(UUID branchId,
                                      UUID productId,
                                      String productSku,
                                      BigDecimal requested,
                                      BigDecimal available) {
        super(DomainErrorCode.INSUFFICIENT_STOCK,
                "Stock insuficiente para el producto '%s': se solicitaron %s y hay %s disponibles."
                        .formatted(productSku, requested.toPlainString(), available.toPlainString()),
                buildDetails(branchId, productId, productSku, requested, available));
        this.requested = requested;
        this.available = available;
    }

    private static Map<String, Object> buildDetails(UUID branchId,
                                                    UUID productId,
                                                    String productSku,
                                                    BigDecimal requested,
                                                    BigDecimal available) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "RN-03");
        details.put("branchId", String.valueOf(branchId));
        details.put("productId", String.valueOf(productId));
        details.put("productSku", productSku);
        details.put("requestedQuantity", requested.toPlainString());
        details.put("availableQuantity", available.toPlainString());
        return details;
    }

    public BigDecimal getRequested() {
        return requested;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
