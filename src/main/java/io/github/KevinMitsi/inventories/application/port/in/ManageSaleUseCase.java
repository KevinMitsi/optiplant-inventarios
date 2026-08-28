package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateSaleCommand;
import io.github.KevinMitsi.inventories.domain.model.Sale;

import java.util.UUID;

public interface ManageSaleUseCase {

    Sale createSale(CreateSaleCommand command);

    /** Descuenta inventario mediante {@code SALE_OUT}, validando stock disponible (RN-03). */
    Sale confirmSale(UUID saleId);

    /** Si la venta estaba confirmada, restituye el inventario con un movimiento compensatorio. */
    Sale cancelSale(UUID saleId);
}
