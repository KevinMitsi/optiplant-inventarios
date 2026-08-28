package io.github.KevinMitsi.inventories.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Demanda de un producto en un período (RF-44, HU-39): cantidad total vendida en ventas
 * confirmadas, para distinguir productos de alta rotación (encabezan la lista, orden
 * descendente) de los de baja rotación (al final, incluidos los que no tuvieron ninguna
 * venta en el período).
 *
 * <p>Proyección de solo lectura, mismo criterio que {@code SalesSummary}.
 *
 * @param quantitySold cantidad total vendida en el período, en unidad base; {@code ZERO} si
 *                      no tuvo ventas
 * @param saleCount     cantidad de ventas confirmadas que incluyeron el producto
 */
public record ProductRotation(UUID productId, String productName, BigDecimal quantitySold, long saleCount) {
}
