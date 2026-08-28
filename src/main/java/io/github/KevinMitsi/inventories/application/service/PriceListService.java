package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManagePriceListUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryPriceListUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreatePriceListCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetProductPriceCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdatePriceListCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.PriceListSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PriceListRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductPriceRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PriceList;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.ProductPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Listas de precios y precios por producto (RF-29, HU-25). */
@Service
@Transactional(rollbackFor = Exception.class)
public class PriceListService implements ManagePriceListUseCase, QueryPriceListUseCase {

    private static final Logger log = LoggerFactory.getLogger(PriceListService.class);

    private static final String PRICE_LIST = "la lista de precios";
    private static final String PRODUCT = "el producto";
    private static final String ORGANIZATION = "la organización";
    private static final String PRODUCT_PRICE = "el precio del producto";

    private final PriceListRepositoryPort priceListRepository;
    private final ProductPriceRepositoryPort productPriceRepository;
    private final OrganizationRepositoryPort organizationRepository;
    private final ProductRepositoryPort productRepository;

    public PriceListService(PriceListRepositoryPort priceListRepository,
                            ProductPriceRepositoryPort productPriceRepository,
                            OrganizationRepositoryPort organizationRepository,
                            ProductRepositoryPort productRepository) {
        this.priceListRepository = priceListRepository;
        this.productPriceRepository = productPriceRepository;
        this.organizationRepository = organizationRepository;
        this.productRepository = productRepository;
    }

    @Override
    public PriceList createPriceList(CreatePriceListCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }

        PriceList priceList = PriceList.create(command.organizationId(), command.code(), command.name(),
                command.description(), command.validFrom(), command.validUntil());

        if (priceListRepository.existsByOrganizationIdAndCode(command.organizationId(), priceList.getCode())) {
            throw new DuplicateResourceException(PRICE_LIST, "código", priceList.getCode());
        }

        PriceList saved = priceListRepository.save(priceList);
        log.info("Lista de precios creada: id={}, código={}", saved.getId(), saved.getCode());
        return saved;
    }

    @Override
    public PriceList updatePriceList(UpdatePriceListCommand command) {
        PriceList priceList = loadPriceList(command.priceListId());
        priceList.updateDetails(command.name(), command.description(), command.validFrom(), command.validUntil());
        return priceListRepository.save(priceList);
    }

    @Override
    public PriceList deactivatePriceList(UUID priceListId) {
        PriceList priceList = loadPriceList(priceListId);
        priceList.deactivate();
        return priceListRepository.save(priceList);
    }

    @Override
    public PriceList activatePriceList(UUID priceListId) {
        PriceList priceList = loadPriceList(priceListId);
        priceList.activate();
        return priceListRepository.save(priceList);
    }

    @Override
    public ProductPrice setProductPrice(SetProductPriceCommand command) {
        loadPriceList(command.priceListId());
        Product product = requireProduct(command.productId());
        requireProductUnit(product, command.productUnitId());

        ProductPrice price = productPriceRepository
                .findByPriceListIdAndProductIdAndProductUnitId(
                        command.priceListId(), command.productId(), command.productUnitId())
                .map(existing -> {
                    existing.changePrice(Money.of(command.price()));
                    return existing;
                })
                .orElseGet(() -> ProductPrice.create(
                        command.priceListId(), command.productId(), command.productUnitId(),
                        Money.of(command.price())));

        ProductPrice saved = productPriceRepository.save(price);
        log.info("Precio fijado: lista={}, producto={}, precio={}",
                command.priceListId(), command.productId(), saved.getPrice());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public PriceList getPriceListById(UUID priceListId) {
        return loadPriceList(priceListId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PriceList> searchPriceLists(PriceListSearchCriteria criteria, PageQuery pageQuery) {
        return priceListRepository.search(criteria, pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPrice getProductPrice(UUID priceListId, UUID productId, UUID productUnitId) {
        return productPriceRepository.findByPriceListIdAndProductIdAndProductUnitId(priceListId, productId, productUnitId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT_PRICE,
                        "lista %s / producto %s".formatted(priceListId, productId)));
    }

    private PriceList loadPriceList(UUID priceListId) {
        return priceListRepository.findById(priceListId)
                .orElseThrow(() -> new ResourceNotFoundException(PRICE_LIST, priceListId));
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, productId));
    }

    private void requireProductUnit(Product product, UUID productUnitId) {
        product.findUnitById(productUnitId)
                .orElseThrow(() -> new DomainValidationException("productUnitId",
                        "La presentación indicada no pertenece al producto '%s'.".formatted(product.getSku())));
    }
}
