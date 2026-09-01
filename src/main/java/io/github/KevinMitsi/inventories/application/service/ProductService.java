package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.AddProductVariantCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.result.ProductFamily;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.usecase.ProductUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Primary
@Service
@Transactional(rollbackFor = Exception.class)
public class ProductService implements ManageProductUseCase, QueryProductUseCase {

    private final ProductUseCase useCase;

    public ProductService(ProductUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public ProductFamily createProduct(CreateProductCommand command) {
        return useCase.createProduct(command);
    }

    @Override
    public Product updateProduct(UpdateProductCommand command) {
        return useCase.updateProduct(command);
    }

    @Override
    public Product addVariant(AddProductVariantCommand command) {
        return useCase.addVariant(command);
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
    public ProductFamily getProductFamily(UUID productId) {
        return useCase.getProductFamily(productId);
    }

    @Override
    public Product getProductBySku(UUID organizationId, String sku) {
        return useCase.getProductBySku(organizationId, sku);
    }

    @Override
    public List<Product> listVariants(UUID parentProductId) {
        return useCase.listVariants(parentProductId);
    }

    @Override
    public PageResult<Product> searchProducts(ProductSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchProducts(criteria, pageQuery);
    }
}
