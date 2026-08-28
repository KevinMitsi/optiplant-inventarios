package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryInventoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryEntryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryExitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetMinimumStockCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.InventorySearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryMovementRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta de saldos y registro manual de entradas/salidas de inventario (EP-04).
 *
 * <p>El registro con documento —compra, venta, transferencia, ajuste formal— no pasa por
 * aquí: cada módulo posee su propio flujo y delega en {@link InventoryMovementPoster}
 * directamente. Este servicio cubre lo que HU-12/HU-13 piden como movimiento libre, sin
 * documento de origen.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class InventoryService implements ManageInventoryUseCase, QueryInventoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private static final String BRANCH = "la sucursal";
    private static final String PRODUCT = "el producto";
    private static final String INVENTORY = "el inventario";

    private final InventoryRepositoryPort inventoryRepository;
    private final InventoryMovementRepositoryPort movementRepository;
    private final BranchRepositoryPort branchRepository;
    private final ProductRepositoryPort productRepository;
    private final InventoryMovementPoster poster;

    InventoryService(InventoryRepositoryPort inventoryRepository,
                     InventoryMovementRepositoryPort movementRepository,
                     BranchRepositoryPort branchRepository,
                     ProductRepositoryPort productRepository,
                     InventoryMovementPoster poster) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.branchRepository = branchRepository;
        this.productRepository = productRepository;
        this.poster = poster;
    }

    @Override
    public Inventory setMinimumStock(SetMinimumStockCommand command) {
        requireBranch(command.branchId());
        requireProduct(command.productId());

        Inventory inventory = inventoryRepository
                .findByBranchIdAndProductId(command.branchId(), command.productId())
                .orElseGet(() -> Inventory.open(command.branchId(), command.productId()));

        inventory.setMinimumStock(Quantity.of(command.minimumStock()));

        Inventory saved = inventoryRepository.save(inventory);
        log.info("Stock mínimo actualizado: sucursal={}, producto={}, mínimo={}",
                saved.getBranchId(), saved.getProductId(), saved.getMinimumStock());
        return saved;
    }

    @Override
    public InventoryMovement registerEntry(RegisterInventoryEntryCommand command) {
        Product product = requireProduct(command.productId());
        requireBranch(command.branchId());

        return poster.post(PostInventoryMovementCommand.withoutReference(
                command.branchId(), command.productId(), product.getSku(),
                InventoryMovementType.RETURN_IN, command.quantity(), command.reason(), command.userId()));
    }

    @Override
    public InventoryMovement registerExit(RegisterInventoryExitCommand command) {
        Product product = requireProduct(command.productId());
        requireBranch(command.branchId());

        return poster.post(PostInventoryMovementCommand.withoutReference(
                command.branchId(), command.productId(), product.getSku(),
                InventoryMovementType.LOSS_OUT, command.quantity(), command.reason(), command.userId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Inventory getByBranchAndProduct(UUID branchId, UUID productId) {
        return inventoryRepository.findByBranchIdAndProductId(branchId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(INVENTORY,
                        "sucursal %s / producto %s".formatted(branchId, productId)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Inventory> searchInventory(InventorySearchCriteria criteria, PageQuery pageQuery) {
        return inventoryRepository.search(criteria, pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<InventoryMovement> getMovementHistory(UUID branchId, UUID productId, PageQuery pageQuery) {
        Inventory inventory = getByBranchAndProduct(branchId, productId);
        return movementRepository.findByInventoryId(inventory.getId(), pageQuery);
    }

    private void requireBranch(UUID branchId) {
        if (!branchRepository.existsById(branchId)) {
            throw new ResourceNotFoundException(BRANCH, branchId);
        }
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, productId));
    }
}
