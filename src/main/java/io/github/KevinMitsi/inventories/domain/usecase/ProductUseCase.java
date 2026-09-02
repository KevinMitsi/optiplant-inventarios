package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryProductUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.AddProductVariantCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.result.ProductFamily;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class ProductUseCase implements ManageProductUseCase, QueryProductUseCase {

    private static final Logger log = Logger.getLogger(ProductUseCase.class.getName());

    private static final String PRODUCT = "el producto";
    private static final String CATEGORY = "la categoría";
    private static final String UNIT = "la unidad de medida";
    private static final String ORGANIZATION = "la organización";
    private static final String SKU_LABEL = "SKU";
    private static final String BARCODE_LABEL = "código de barras";

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
    public ProductFamily createProduct(CreateProductCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }

        UUID organizationId = command.organizationId();
        String sku = requireFreeSku(organizationId, command.sku(), Set.of());
        String barcode = requireFreeBarcode(organizationId, command.barcode(), Set.of());

        validateCategory(command.categoryId(), organizationId);
        UnitOfMeasure unit = loadUnit(command.unitOfMeasureId());

        Product principal = Product.create(organizationId, command.categoryId(), sku, barcode,
                command.name(), command.description(), unit);

        // Los SKU y códigos de barras del propio lote todavía no están en la base, así que la
        // consulta de unicidad no los ve: se acumulan aquí para detectar el choque entre dos
        // variantes de la misma petición.
        Set<String> pendingSkus = new HashSet<>(Set.of(sku));
        Set<String> pendingBarcodes = new HashSet<>();
        if (barcode != null) {
            pendingBarcodes.add(barcode);
        }

        List<Product> variants = new ArrayList<>();
        for (CreateProductCommand.Variant variant : command.variants()) {
            variants.add(buildVariant(principal, variant.sku(), variant.barcode(), variant.name(),
                    variant.description(), variant.categoryId(), variant.unitOfMeasureId(),
                    pendingSkus, pendingBarcodes));
        }

        Product savedPrincipal = productRepository.save(principal);
        List<Product> savedVariants = variants.stream().map(productRepository::save).toList();

        log.info(() -> "Producto creado: id=%s, sku=%s, unidad=%s, variantes=%d"
                .formatted(savedPrincipal.getId(), savedPrincipal.getSku(),
                        savedPrincipal.getUnit().code(), savedVariants.size()));

        return new ProductFamily(savedPrincipal, savedVariants);
    }

    @Override
    public Product addVariant(AddProductVariantCommand command) {
        Product parent = loadProduct(command.parentProductId());

        Product variant = buildVariant(parent, command.sku(), command.barcode(), command.name(),
                command.description(), command.categoryId(), command.unitOfMeasureId(),
                new HashSet<>(), new HashSet<>());

        Product saved = productRepository.save(variant);
        log.info(() -> "Variante creada: id=%s, sku=%s, principal=%s"
                .formatted(saved.getId(), saved.getSku(), parent.getSku()));
        return saved;
    }

    @Override
    public Product updateProduct(UpdateProductCommand command) {
        Product product = loadProduct(command.productId());

        String barcode = normalizeBarcode(command.barcode());
        boolean barcodeChanged = barcode != null && !barcode.equals(product.getBarcode());
        if (barcodeChanged
                && productRepository.existsByOrganizationIdAndBarcode(product.getOrganizationId(), barcode)) {
            throw new DuplicateResourceException(PRODUCT, BARCODE_LABEL, barcode);
        }

        validateCategory(command.categoryId(), product.getOrganizationId());

        product.updateDetails(command.categoryId(), barcode, command.name(), command.description());
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
    public ProductFamily getProductFamily(UUID productId) {
        Product product = loadProduct(productId);
        if (product.isVariant()) {
            return ProductFamily.of(product);
        }
        return new ProductFamily(product, productRepository.findVariants(productId));
    }

    @Override
    public Product getProductBySku(UUID organizationId, String sku) {
        String normalizedSku = normalizeSku(sku);
        return productRepository.findByOrganizationIdAndSku(organizationId, normalizedSku)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, SKU_LABEL, normalizedSku));
    }

    @Override
    public List<Product> listVariants(UUID parentProductId) {
        return productRepository.findVariants(loadProduct(parentProductId).getId());
    }

    @Override
    public PageResult<Product> searchProducts(ProductSearchCriteria criteria, PageQuery pageQuery) {
        return productRepository.search(criteria, pageQuery);
    }

    private Product buildVariant(Product parent,
                                 String sku,
                                 String barcode,
                                 String name,
                                 String description,
                                 UUID categoryId,
                                 UUID unitOfMeasureId,
                                 Set<String> pendingSkus,
                                 Set<String> pendingBarcodes) {
        UUID organizationId = parent.getOrganizationId();

        String normalizedSku = requireFreeSku(organizationId, sku, pendingSkus);
        String normalizedBarcode = requireFreeBarcode(organizationId, barcode, pendingBarcodes);

        validateCategory(categoryId, organizationId);
        UnitOfMeasure unit = unitOfMeasureId == null ? null : loadUnit(unitOfMeasureId);

        Product variant = parent.createVariant(normalizedSku, normalizedBarcode, name, description,
                categoryId, unit);

        pendingSkus.add(normalizedSku);
        if (normalizedBarcode != null) {
            pendingBarcodes.add(normalizedBarcode);
        }
        return variant;
    }

    private String requireFreeSku(UUID organizationId, String sku, Set<String> pendingSkus) {
        String normalized = normalizeSku(sku);
        if (normalized != null
                && (pendingSkus.contains(normalized)
                    || productRepository.existsByOrganizationIdAndSku(organizationId, normalized))) {
            throw new DuplicateResourceException(PRODUCT, SKU_LABEL, normalized);
        }
        return normalized;
    }

    private String requireFreeBarcode(UUID organizationId, String barcode, Set<String> pendingBarcodes) {
        String normalized = normalizeBarcode(barcode);
        if (normalized != null
                && (pendingBarcodes.contains(normalized)
                    || productRepository.existsByOrganizationIdAndBarcode(organizationId, normalized))) {
            throw new DuplicateResourceException(PRODUCT, BARCODE_LABEL, normalized);
        }
        return normalized;
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
