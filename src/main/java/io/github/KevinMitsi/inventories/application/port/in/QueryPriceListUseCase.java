package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.PriceListSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PriceList;
import io.github.KevinMitsi.inventories.domain.model.ProductPrice;

import java.util.UUID;

public interface QueryPriceListUseCase {

    PriceList getPriceListById(UUID priceListId);

    PageResult<PriceList> searchPriceLists(PriceListSearchCriteria criteria, PageQuery pageQuery);

    ProductPrice getProductPrice(UUID priceListId, UUID productId, UUID productUnitId);
}
