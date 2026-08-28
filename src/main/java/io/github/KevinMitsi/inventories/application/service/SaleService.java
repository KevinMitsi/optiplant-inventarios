package io.github.KevinMitsi.inventories.application.service;

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
import io.github.KevinMitsi.inventories.domain.model.ProductUnit;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.Sale;
import io.github.KevinMitsi.inventories.domain.model.SaleItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ventas: creación, confirmación y cancelación (EP-06).
 *
 * <p>Confirmar es donde vive, sin duplicarla, la regla que RN-03 exige: este servicio no
 * comprueba el stock disponible por su cuenta, delega cada línea en
 * {@link InventoryMovementPoster#post}, que ya se niega a descontar más de lo que hay. Si
 * cualquier línea no cabe, la excepción revierte la transacción completa — la venta ni
 * siquiera queda confirmada a medias.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class SaleService implements ManageSaleUseCase, QuerySaleUseCase {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

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

    SaleService(SaleRepositoryPort saleRepository,
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
        log.info("Venta creada: id={}, número={}, líneas={}", saved.getId(), saved.getSaleNumber(), items.size());
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

        log.info("Venta confirmada: id={}, número={}", saved.getId(), saved.getSaleNumber());
        return saved;
    }

    @Override
    public Sale cancelSale(UUID saleId) {
        Sale sale = loadSale(saleId);
        boolean mustRestock = sale.getStatus() == io.github.KevinMitsi.inventories.domain.model.SaleStatus.CONFIRMED;

        sale.cancel();
        Sale saved = saleRepository.save(sale);

        if (mustRestock) {
            for (SaleItem item : saved.getItems()) {
                postMovement(saved, item, InventoryMovementType.RETURN_IN,
                        "Cancelación de venta %s".formatted(saved.getSaleNumber()));
            }
        }

        log.info("Venta cancelada: id={}, número={}, restituyó inventario={}",
                saved.getId(), saved.getSaleNumber(), mustRestock);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Sale getSaleById(UUID saleId) {
        return loadSale(saleId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Sale> searchSales(SaleSearchCriteria criteria, PageQuery pageQuery) {
        return saleRepository.search(criteria, pageQuery);
    }

    private void postMovement(Sale sale, SaleItem item, InventoryMovementType type, String reason) {
        Product product = requireProduct(item.getProductId());
        ProductUnit productUnit = requireProductUnit(product, item.getProductUnitId());
        Quantity baseQuantity = item.getQuantity().toBaseUnit(productUnit.getConversionFactor());

        poster.post(new PostInventoryMovementCommand(sale.getBranchId(), item.getProductId(), product.getSku(),
                type, baseQuantity.value(), null, reason, sale.getCreatedBy(), Instant.now(),
                null, sale.getId(), null, null));
    }

    private SaleItem toItem(CreateSaleCommand.Item item, UUID priceListId) {
        Product product = requireProduct(item.productId());
        requireProductUnit(product, item.productUnitId());

        Money unitPrice = item.unitPrice() != null
                ? Money.of(item.unitPrice())
                : resolvePriceFromList(priceListId, item.productId(), item.productUnitId());

        return SaleItem.create(item.productId(), item.productUnitId(), Quantity.of(item.quantity()), unitPrice,
                Percentage.ofNullable(item.discountPercentage()));
    }

    private Money resolvePriceFromList(UUID priceListId, UUID productId, UUID productUnitId) {
        if (priceListId == null) {
            throw new DomainValidationException("unitPrice",
                    "La línea no indica precio y la venta no tiene lista de precios asociada (HU-25).");
        }
        return productPriceRepository.findByPriceListIdAndProductIdAndProductUnitId(priceListId, productId, productUnitId)
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

    private ProductUnit requireProductUnit(Product product, UUID productUnitId) {
        return product.findUnitById(productUnitId)
                .orElseThrow(() -> new DomainValidationException("productUnitId",
                        "La presentación indicada no pertenece al producto '%s'.".formatted(product.getSku())));
    }
}
