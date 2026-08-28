package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManagePriceListUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryPriceListUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreatePriceListCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetProductPriceCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdatePriceListCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.PriceListSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PriceList;
import io.github.KevinMitsi.inventories.domain.model.ProductPrice;
import io.github.KevinMitsi.inventories.domain.usecase.PriceListUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class PriceListService implements ManagePriceListUseCase, QueryPriceListUseCase {

    private final PriceListUseCase useCase;

    public PriceListService(PriceListUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public PriceList createPriceList(CreatePriceListCommand command) {
        return useCase.createPriceList(command);
    }

    @Override
    public PriceList updatePriceList(UpdatePriceListCommand command) {
        return useCase.updatePriceList(command);
    }

    @Override
    public PriceList deactivatePriceList(UUID priceListId) {
        return useCase.deactivatePriceList(priceListId);
    }

    @Override
    public PriceList activatePriceList(UUID priceListId) {
        return useCase.activatePriceList(priceListId);
    }

    @Override
    public ProductPrice setProductPrice(SetProductPriceCommand command) {
        return useCase.setProductPrice(command);
    }

    @Override
    public PriceList getPriceListById(UUID priceListId) {
        return useCase.getPriceListById(priceListId);
    }

    @Override
    public PageResult<PriceList> searchPriceLists(PriceListSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchPriceLists(criteria, pageQuery);
    }

    @Override
    public ProductPrice getProductPrice(UUID priceListId, UUID productId, UUID productUnitId) {
        return useCase.getProductPrice(priceListId, productId, productUnitId);
    }
}
