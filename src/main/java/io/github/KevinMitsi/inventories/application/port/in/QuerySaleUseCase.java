package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.SaleSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Sale;

import java.util.UUID;

public interface QuerySaleUseCase {

    /** El comprobante consultable de HU-26: producto, cantidad, precio y responsable. */
    Sale getSaleById(UUID saleId);

    PageResult<Sale> searchSales(SaleSearchCriteria criteria, PageQuery pageQuery);
}
