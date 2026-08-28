package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.AddProductUnitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ChangeBaseUnitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ChangeProductUnitFactorCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.usecase.ProductUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class ProductService implements ManageProductUseCase, QueryProductUseCase {

    private final ProductUseCase useCase;

    public ProductService(ProductUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Product createProduct(CreateProductCommand command) {
        return useCase.createProduct(command);
    }

    @Override
    public Product updateProduct(UpdateProductCommand command) {
        return useCase.updateProduct(command);
    }

    @Override
    public Product addUnit(AddProductUnitCommand command) {
        return useCase.addUnit(command);
    }

    @Override
    public Product changeUnitFactor(ChangeProductUnitFactorCommand command) {
        return useCase.changeUnitFactor(command);
    }

    @Override
    public Product changeBaseUnit(ChangeBaseUnitCommand command) {
        return useCase.changeBaseUnit(command);
    }

    @Override
    public Product deactivateUnit(UUID productId, UUID productUnitId) {
        return useCase.deactivateUnit(productId, productUnitId);
    }

    @Override
    public Product activateUnit(UUID productId, UUID productUnitId) {
        return useCase.activateUnit(productId, productUnitId);
    }

    @Override
    public Product deactivateProduct(UUID productId) {
        return useCase.deactivateProduct(productId);
    }

    @Override
    public Product activateProduct(UUID productId) {
        return useCase.activateProduct(productId);
    }

    @Override
    public Product getProductById(UUID productId) {
        return useCase.getProductById(productId);
    }

    @Override
    public Product getProductBySku(UUID organizationId, String sku) {
        return useCase.getProductBySku(organizationId, sku);
    }

    @Override
    public PageResult<Product> searchProducts(ProductSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchProducts(criteria, pageQuery);
    }
}
