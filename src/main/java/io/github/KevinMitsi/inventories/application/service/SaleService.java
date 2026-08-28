package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageSaleUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QuerySaleUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateSaleCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.SaleSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Sale;
import io.github.KevinMitsi.inventories.domain.usecase.SaleUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class SaleService implements ManageSaleUseCase, QuerySaleUseCase {

    private final SaleUseCase useCase;

    public SaleService(SaleUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Sale createSale(CreateSaleCommand command) {
        return useCase.createSale(command);
    }

    @Override
    public Sale confirmSale(UUID saleId) {
        return useCase.confirmSale(saleId);
    }

    @Override
    public Sale cancelSale(UUID saleId) {
        return useCase.cancelSale(saleId);
    }

    @Override
    public Sale getSaleById(UUID saleId) {
        return useCase.getSaleById(saleId);
    }

    @Override
    public PageResult<Sale> searchSales(SaleSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchSales(criteria, pageQuery);
    }
}
