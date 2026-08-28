package io.github.KevinMitsi.inventories.application.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Ajustes de inventario formales: corrección con varias líneas, responsable y aprobador
 * (ENTITIES.md §18).
 *
 * <p>Confirmar el ajuste ({@link #approveAdjustment}) es lo que mueve stock de verdad: postea
 * un movimiento {@code ADJUSTMENT_IN}/{@code ADJUSTMENT_OUT} por línea a través de
 * {@link InventoryMovementPoster}, referenciando este documento. Antes de aprobarse, el
 * ajuste es solo una propuesta sin efecto sobre ningún saldo.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class InventoryAdjustmentService implements ManageInventoryAdjustmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(InventoryAdjustmentService.class);

    private static final String BRANCH = "la sucursal";
    private static final String PRODUCT = "el producto";
    private static final String ADJUSTMENT = "el ajuste de inventario";

    private final InventoryAdjustmentRepositoryPort adjustmentRepository;
    private final BranchRepositoryPort branchRepository;
    private final ProductRepositoryPort productRepository;
    private final InventoryMovementPoster poster;

    InventoryAdjustmentService(InventoryAdjustmentRepositoryPort adjustmentRepository,
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
        log.info("Ajuste de inventario creado: id={}, sucursal={}, líneas={}",
                saved.getId(), saved.getBranchId(), saved.getItems().size());
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

        log.info("Ajuste de inventario aprobado: id={}, aprobador={}", saved.getId(), approvedBy);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
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
