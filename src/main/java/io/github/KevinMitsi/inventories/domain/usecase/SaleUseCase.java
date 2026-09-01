package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageSaleUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QuerySaleUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateSaleCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.SaleSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PriceListRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductPriceRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.SaleRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Percentage;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.Sale;
import io.github.KevinMitsi.inventories.domain.model.SaleItem;
import io.github.KevinMitsi.inventories.domain.model.SaleStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class SaleUseCase implements ManageSaleUseCase, QuerySaleUseCase {

    private static final Logger log = Logger.getLogger(SaleUseCase.class.getName());

    private static final String BRANCH = "la sucursal";
    private static final String PRODUCT = "el producto";
    private static final String PRICE_LIST = "la lista de precios";
    private static final String SALE = "la venta";

    private final SaleRepositoryPort saleRepository;
    private final BranchRepositoryPort branchRepository;
    private final ProductRepositoryPort productRepository;
    private final PriceListRepositoryPort priceListRepository;
    private final ProductPriceRepositoryPort productPriceRepository;
    private final InventoryMovementPoster poster;

    public SaleUseCase(SaleRepositoryPort saleRepository,
              BranchRepositoryPort branchRepository,
              ProductRepositoryPort productRepository,
              PriceListRepositoryPort priceListRepository,
              ProductPriceRepositoryPort productPriceRepository,
              InventoryMovementPoster poster) {
        this.saleRepository = saleRepository;
        this.branchRepository = branchRepository;
        this.productRepository = productRepository;
        this.priceListRepository = priceListRepository;
        this.productPriceRepository = productPriceRepository;
        this.poster = poster;
    }

    @Override
    public Sale createSale(CreateSaleCommand command) {
        if (!branchRepository.existsById(command.branchId())) {
            throw new ResourceNotFoundException(BRANCH, command.branchId());
        }
        if (command.priceListId() != null && !priceListRepository.existsById(command.priceListId())) {
            throw new ResourceNotFoundException(PRICE_LIST, command.priceListId());
        }
        if (saleRepository.existsByBranchIdAndSaleNumber(command.branchId(), command.saleNumber())) {
            throw new DuplicateResourceException(SALE, "número", command.saleNumber());
        }

        List<SaleItem> items = command.items().stream()
                .map(item -> toItem(item, command.priceListId()))
                .toList();

        Sale sale = Sale.create(command.branchId(), command.createdBy(), command.priceListId(),
                command.saleNumber(), command.saleDate(), command.notes(), items);

        Sale saved = saleRepository.save(sale);
        log.info(() -> "Venta creada: id=%s, número=%s, líneas=%d"
                .formatted(saved.getId(), saved.getSaleNumber(), items.size()));
        return saved;
    }

    @Override
    public Sale confirmSale(UUID saleId) {
        Sale sale = loadSale(saleId);
        sale.confirm();
        Sale saved = saleRepository.save(sale);

        for (SaleItem item : saved.getItems()) {
            postMovement(saved, item, InventoryMovementType.SALE_OUT,
                    "Venta %s".formatted(saved.getSaleNumber()));
        }

        log.info(() -> "Venta confirmada: id=%s, número=%s".formatted(saved.getId(), saved.getSaleNumber()));
        return saved;
    }

    @Override
    public Sale cancelSale(UUID saleId) {
        Sale sale = loadSale(saleId);
        boolean mustRestock = sale.getStatus() == SaleStatus.CONFIRMED;

        sale.cancel();
        Sale saved = saleRepository.save(sale);

        if (mustRestock) {
            for (SaleItem item : saved.getItems()) {
                postMovement(saved, item, InventoryMovementType.RETURN_IN,
                        "Cancelación de venta %s".formatted(saved.getSaleNumber()));
            }
        }

        log.info(() -> "Venta cancelada: id=%s, número=%s, restituyó inventario=%s"
                .formatted(saved.getId(), saved.getSaleNumber(), mustRestock));
        return saved;
    }

    @Override
    public Sale getSaleById(UUID saleId) {
        return loadSale(saleId);
    }

    @Override
    public PageResult<Sale> searchSales(SaleSearchCriteria criteria, PageQuery pageQuery) {
        return saleRepository.search(criteria, pageQuery);
    }

    private void postMovement(Sale sale, SaleItem item, InventoryMovementType type, String reason) {
        Product product = requireProduct(item.getProductId());

        poster.post(new PostInventoryMovementCommand(sale.getBranchId(), item.getProductId(), product.getSku(),
                type, item.getQuantity().value(), null, reason, sale.getCreatedBy(), Instant.now(),
                null, sale.getId(), null, null));
    }

    private SaleItem toItem(CreateSaleCommand.Item item, UUID priceListId) {
        requireProduct(item.productId());

        Money unitPrice = item.unitPrice() != null
                ? Money.of(item.unitPrice())
                : resolvePriceFromList(priceListId, item.productId());

        return SaleItem.create(item.productId(), Quantity.of(item.quantity()), unitPrice,
                Percentage.ofNullable(item.discountPercentage()));
    }

    private Money resolvePriceFromList(UUID priceListId, UUID productId) {
        if (priceListId == null) {
            throw new DomainValidationException("unitPrice",
                    "La línea no indica precio y la venta no tiene lista de precios asociada (HU-25).");
        }
        return productPriceRepository.findByPriceListIdAndProductId(priceListId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("el precio del producto",
                        "lista %s / producto %s".formatted(priceListId, productId)))
                .getPrice();
    }

    private Sale loadSale(UUID saleId) {
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException(SALE, saleId));
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, productId));
    }
}
