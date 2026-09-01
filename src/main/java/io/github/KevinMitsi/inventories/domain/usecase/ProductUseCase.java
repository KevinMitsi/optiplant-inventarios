package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.AddProductUnitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ChangeBaseUnitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ChangeProductUnitFactorCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.CategoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UnitOfMeasureRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ProductUseCase implements ManageProductUseCase, QueryProductUseCase {

    private static final Logger log = Logger.getLogger(ProductUseCase.class.getName());

    private static final String PRODUCT = "el producto";
    private static final String CATEGORY = "la categoría";
    private static final String UNIT = "la unidad de medida";
    private static final String ORGANIZATION = "la organización";

    private final ProductRepositoryPort productRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final UnitOfMeasureRepositoryPort unitRepository;
    private final OrganizationRepositoryPort organizationRepository;

    public ProductUseCase(ProductRepositoryPort productRepository,
                          CategoryRepositoryPort categoryRepository,
                          UnitOfMeasureRepositoryPort unitRepository,
                          OrganizationRepositoryPort organizationRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public Product createProduct(CreateProductCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }

        String normalizedSku = normalizeSku(command.sku());
        if (productRepository.existsByOrganizationIdAndSku(command.organizationId(), normalizedSku)) {
            throw new DuplicateResourceException(PRODUCT, "SKU", normalizedSku);
        }

        String barcode = normalizeBarcode(command.barcode());
        if (barcode != null
                && productRepository.existsByOrganizationIdAndBarcode(command.organizationId(), barcode)) {
            throw new DuplicateResourceException(PRODUCT, "código de barras", barcode);
        }

        validateCategory(command.categoryId(), command.organizationId());
        UnitOfMeasure baseUnit = loadUnit(command.baseUnitId());

        Product product = Product.create(
                command.organizationId(),
                command.categoryId(),
                normalizedSku,
                barcode,
                command.name(),
                command.description(),
                baseUnit);

        Product saved = productRepository.save(product);
        log.info(() -> "Producto creado: id=%s, sku=%s, unidadBase=%s"
                .formatted(saved.getId(), saved.getSku(), baseUnit.code()));
        return saved;
    }

    @Override
    public Product updateProduct(UpdateProductCommand command) {
        Product product = loadProduct(command.productId());

        String barcode = normalizeBarcode(command.barcode());
        boolean barcodeChanged = barcode != null && !barcode.equals(product.getBarcode());
        if (barcodeChanged
                && productRepository.existsByOrganizationIdAndBarcode(product.getOrganizationId(), barcode)) {
            throw new DuplicateResourceException(PRODUCT, "código de barras", barcode);
        }

        validateCategory(command.categoryId(), product.getOrganizationId());

        product.updateDetails(command.categoryId(), barcode, command.name(), command.description());
        return productRepository.save(product);
    }

    @Override
    public Product addUnit(AddProductUnitCommand command) {
        Product product = loadProduct(command.productId());
        UnitOfMeasure unit = loadUnit(command.unitOfMeasureId());

        product.addUnit(unit, command.conversionFactor());

        Product saved = productRepository.save(product);
        log.info(() -> "Presentación añadida al producto %s: unidad=%s, factor=%s"
                .formatted(saved.getSku(), unit.code(), command.conversionFactor()));
        return saved;
    }

    @Override
    public Product changeUnitFactor(ChangeProductUnitFactorCommand command) {
        Product product = loadProduct(command.productId());
        product.changeUnitFactor(command.productUnitId(), command.conversionFactor());
        return productRepository.save(product);
    }

    @Override
    public Product changeBaseUnit(ChangeBaseUnitCommand command) {
        Product product = loadProduct(command.productId());
        product.changeBaseUnit(command.newBaseProductUnitId(), command.previousBaseNewFactor());

        // Degrada la base anterior en su propia sentencia antes del merge del agregado: el
        // orden de flush de Hibernate entre hijos del mismo tipo no está garantizado y podría
        // promover la nueva base antes de degradar la anterior, violando
        // ux_product_unit_single_base.
        productRepository.clearBaseUnit(command.productId());
        Product saved = productRepository.save(product);
        log.info(() -> "Unidad base cambiada en el producto %s".formatted(saved.getSku()));
        return saved;
    }

    @Override
    public Product deactivateUnit(UUID productId, UUID productUnitId) {
        Product product = loadProduct(productId);
        product.deactivateUnit(productUnitId);
        return productRepository.save(product);
    }

    @Override
    public Product activateUnit(UUID productId, UUID productUnitId) {
        Product product = loadProduct(productId);
        product.activateUnit(productUnitId);
        return productRepository.save(product);
    }

    @Override
    public Product deactivateProduct(UUID productId) {
        Product product = loadProduct(productId);
        product.deactivate();

        Product saved = productRepository.save(product);
        log.info(() -> "Producto desactivado: id=%s, sku=%s".formatted(saved.getId(), saved.getSku()));
        return saved;
    }

    @Override
    public Product activateProduct(UUID productId) {
        Product product = loadProduct(productId);
        product.activate();
        return productRepository.save(product);
    }

    @Override
    public Product getProductById(UUID productId) {
        return loadProduct(productId);
    }

    @Override
    public Product getProductBySku(UUID organizationId, String sku) {
        String normalizedSku = normalizeSku(sku);
        return productRepository.findByOrganizationIdAndSku(organizationId, normalizedSku)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, "SKU", normalizedSku));
    }

    @Override
    public PageResult<Product> searchProducts(ProductSearchCriteria criteria, PageQuery pageQuery) {
        return productRepository.search(criteria, pageQuery);
    }

    private void validateCategory(UUID categoryId, UUID organizationId) {
        if (categoryId == null) {
            return;
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(CATEGORY, categoryId));

        if (!category.getOrganizationId().equals(organizationId)) {
            throw new BusinessRuleViolationException("RF-07",
                    "La categoría indicada pertenece a otra organización.",
                    Map.of("categoryId", String.valueOf(categoryId)));
        }

        if (!category.isActive()) {
            throw new BusinessRuleViolationException("RF-07",
                    "No se puede clasificar un producto en una categoría dada de baja.",
                    Map.of("categoryId", String.valueOf(categoryId)));
        }
    }

    private Product loadProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, productId));
    }

    private UnitOfMeasure loadUnit(UUID unitId) {
        if (unitId == null) {
            throw new ResourceNotFoundException(UNIT, null);
        }
        return unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException(UNIT, unitId));
    }

    private String normalizeSku(String sku) {
        return sku == null ? null : sku.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return null;
        }
        return barcode.trim();
    }
}
