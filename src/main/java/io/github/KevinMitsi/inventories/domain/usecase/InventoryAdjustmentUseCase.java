package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryAdjustmentUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateInventoryAdjustmentCommand;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAdjustmentRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustment;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustmentItem;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Product;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class InventoryAdjustmentUseCase implements ManageInventoryAdjustmentUseCase {

    private static final Logger log = Logger.getLogger(InventoryAdjustmentUseCase.class.getName());

    private static final String BRANCH = "la sucursal";
    private static final String PRODUCT = "el producto";
    private static final String ADJUSTMENT = "el ajuste de inventario";

    private final InventoryAdjustmentRepositoryPort adjustmentRepository;
    private final BranchRepositoryPort branchRepository;
    private final ProductRepositoryPort productRepository;
    private final InventoryMovementPoster poster;

    public InventoryAdjustmentUseCase(InventoryAdjustmentRepositoryPort adjustmentRepository,
                               BranchRepositoryPort branchRepository,
                               ProductRepositoryPort productRepository,
                               InventoryMovementPoster poster) {
        this.adjustmentRepository = adjustmentRepository;
        this.branchRepository = branchRepository;
        this.productRepository = productRepository;
        this.poster = poster;
    }

    @Override
    public InventoryAdjustment createAdjustment(CreateInventoryAdjustmentCommand command) {
        if (!branchRepository.existsById(command.branchId())) {
            throw new ResourceNotFoundException(BRANCH, command.branchId());
        }

        List<InventoryAdjustmentItem> items = command.items().stream()
                .map(item -> {
                    requireProduct(item.productId());
                    return InventoryAdjustmentItem.create(item.productId(), item.quantityDelta(), item.reason());
                })
                .toList();

        InventoryAdjustment adjustment = InventoryAdjustment.create(
                command.branchId(), command.createdBy(), command.reason(), items);

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);
        log.info(() -> "Ajuste de inventario creado: id=%s, sucursal=%s, líneas=%d"
                .formatted(saved.getId(), saved.getBranchId(), saved.getItems().size()));
        return saved;
    }

    @Override
    public InventoryAdjustment approveAdjustment(UUID adjustmentId, UUID approvedBy) {
        InventoryAdjustment adjustment = loadAdjustment(adjustmentId);
        adjustment.approve(approvedBy);
        InventoryAdjustment saved = adjustmentRepository.save(adjustment);

        for (InventoryAdjustmentItem item : saved.getItems()) {
            Product product = requireProduct(item.getProductId());
            InventoryMovementType type = item.isEntry()
                    ? InventoryMovementType.ADJUSTMENT_IN
                    : InventoryMovementType.ADJUSTMENT_OUT;

            poster.post(PostInventoryMovementCommand.forAdjustment(
                    saved.getBranchId(), item.getProductId(), product.getSku(), type,
                    item.absoluteQuantity().value(),
                    item.getReason() != null ? item.getReason() : saved.getReason(),
                    approvedBy, saved.getId()));
        }

        log.info(() -> "Ajuste de inventario aprobado: id=%s, aprobador=%s".formatted(saved.getId(), approvedBy));
        return saved;
    }

    @Override
    public InventoryAdjustment getAdjustmentById(UUID adjustmentId) {
        return loadAdjustment(adjustmentId);
    }

    private InventoryAdjustment loadAdjustment(UUID adjustmentId) {
        return adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ADJUSTMENT, adjustmentId));
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, productId));
    }
}
