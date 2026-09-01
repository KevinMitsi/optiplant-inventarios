package io.github.KevinMitsi.inventories.domain.usecase;

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

import java.util.UUID;
import java.util.logging.Logger;

public class PriceListUseCase implements ManagePriceListUseCase, QueryPriceListUseCase {

    private static final Logger log = Logger.getLogger(PriceListUseCase.class.getName());

    private static final String PRICE_LIST = "la lista de precios";
    private static final String PRODUCT = "el producto";
    private static final String ORGANIZATION = "la organización";
    private static final String PRODUCT_PRICE = "el precio del producto";

    private final PriceListRepositoryPort priceListRepository;
    private final ProductPriceRepositoryPort productPriceRepository;
    private final OrganizationRepositoryPort organizationRepository;
    private final ProductRepositoryPort productRepository;

    public PriceListUseCase(PriceListRepositoryPort priceListRepository,
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
        log.info(() -> "Lista de precios creada: id=%s, código=%s".formatted(saved.getId(), saved.getCode()));
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
        requireProduct(command.productId());

        ProductPrice price = productPriceRepository
                .findByPriceListIdAndProductId(command.priceListId(), command.productId())
                .map(existing -> {
                    existing.changePrice(Money.of(command.price()));
                    return existing;
                })
                .orElseGet(() -> ProductPrice.create(
                        command.priceListId(), command.productId(), Money.of(command.price())));

        ProductPrice saved = productPriceRepository.save(price);
        log.info(() -> "Precio fijado: lista=%s, producto=%s, precio=%s"
                .formatted(command.priceListId(), command.productId(), saved.getPrice()));
        return saved;
    }

    @Override
    public PriceList getPriceListById(UUID priceListId) {
        return loadPriceList(priceListId);
    }

    @Override
    public PageResult<PriceList> searchPriceLists(PriceListSearchCriteria criteria, PageQuery pageQuery) {
        return priceListRepository.search(criteria, pageQuery);
    }

    @Override
    public ProductPrice getProductPrice(UUID priceListId, UUID productId) {
        return productPriceRepository.findByPriceListIdAndProductId(priceListId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT_PRICE,
                        "lista %s / producto %s".formatted(priceListId, productId)));
    }

    private PriceList loadPriceList(UUID priceListId) {
        return priceListRepository.findById(priceListId)
                .orElseThrow(() -> new ResourceNotFoundException(PRICE_LIST, priceListId));
    }

    private void requireProduct(UUID productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, productId));
    }
}
