package io.github.KevinMitsi.inventories.domain.usecase;

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

import java.util.UUID;
import java.util.logging.Logger;

public class InventoryUseCase implements ManageInventoryUseCase, QueryInventoryUseCase {

    private static final Logger log = Logger.getLogger(InventoryUseCase.class.getName());

    private static final String BRANCH = "la sucursal";
    private static final String PRODUCT = "el producto";
    private static final String INVENTORY = "el inventario";

    private final InventoryRepositoryPort inventoryRepository;
    private final InventoryMovementRepositoryPort movementRepository;
    private final BranchRepositoryPort branchRepository;
    private final ProductRepositoryPort productRepository;
    private final InventoryMovementPoster poster;

    public InventoryUseCase(InventoryRepositoryPort inventoryRepository,
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
        log.info(() -> "Stock mínimo actualizado: sucursal=%s, producto=%s, mínimo=%s"
                .formatted(saved.getBranchId(), saved.getProductId(), saved.getMinimumStock()));
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
    public Inventory getByBranchAndProduct(UUID branchId, UUID productId) {
        return inventoryRepository.findByBranchIdAndProductId(branchId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(INVENTORY,
                        "sucursal %s / producto %s".formatted(branchId, productId)));
    }

    @Override
    public PageResult<Inventory> searchInventory(InventorySearchCriteria criteria, PageQuery pageQuery) {
        return inventoryRepository.search(criteria, pageQuery);
    }

    @Override
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
