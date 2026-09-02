package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.port.out.InventoryAlertRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryMovementRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.InsufficientStockException;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlertType;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.Quantity;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Único punto que aplica un movimiento de inventario (RN-04): ningún saldo cambia sin una
 * fila en {@code inventory_movement} que lo explique.
 */
public class InventoryMovementPoster {

    private static final Logger log = Logger.getLogger(InventoryMovementPoster.class.getName());

    private final InventoryRepositoryPort inventoryRepository;
    private final InventoryMovementRepositoryPort movementRepository;
    private final InventoryAlertRepositoryPort alertRepository;

    public InventoryMovementPoster(InventoryRepositoryPort inventoryRepository,
                            InventoryMovementRepositoryPort movementRepository,
                            InventoryAlertRepositoryPort alertRepository) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.alertRepository = alertRepository;
    }

    public InventoryMovement post(PostInventoryMovementCommand command) {
        Inventory inventory = inventoryRepository
                .findByBranchIdAndProductId(command.branchId(), command.productId())
                .orElseGet(() -> Inventory.open(command.branchId(), command.productId()));

        Quantity quantity = Quantity.of(command.quantity());
        InventoryMovementType type = command.movementType();

        Money unitCost = applyToInventory(inventory, type, quantity, command);

        Inventory saved = inventoryRepository.save(inventory);

        InventoryMovement movement = InventoryMovement.create(
                saved.getId(), type, command.userId(), quantity, unitCost, command.reason(),
                command.purchaseOrderId(), command.saleId(), command.transferId(), command.adjustmentId(),
                command.occurredAt());
        InventoryMovement savedMovement = movementRepository.save(movement);

        evaluateAlerts(saved);

        log.info(() -> "Movimiento posteado: tipo=%s, sucursal=%s, producto=%s, cantidad=%s"
                .formatted(type, saved.getBranchId(), saved.getProductId(), quantity));
        return savedMovement;
    }

    private Money applyToInventory(Inventory inventory,
                                   InventoryMovementType type,
                                   Quantity quantity,
                                   PostInventoryMovementCommand command) {
        if (type.isExit()) {
            requireSufficientStock(inventory, quantity, command.productSku());
            inventory.decrease(quantity);
            return null;
        }

        if (type == InventoryMovementType.PURCHASE_IN || type == InventoryMovementType.TRANSFER_IN) {
            Money unitCost = Money.of(command.unitCost());
            inventory.receiveWithCost(quantity, unitCost);
            return unitCost;
        }

        inventory.increase(quantity);
        return null;
    }

    private void requireSufficientStock(Inventory inventory, Quantity requested, String productSku) {
        if (inventory.getQuantity().isLessThan(requested)) {
            throw new InsufficientStockException(inventory.getBranchId(), inventory.getProductId(), productSku,
                    requested.value(), inventory.getQuantity().value());
        }
    }

    /**
     * Compara el saldo contra su mínimo configurado y abre, cambia o resuelve la alerta
     * abierta según corresponda.
     *
     * <p>La llama {@link #post} tras cada movimiento, pero también queda disponible para
     * quien cambie el mínimo configurado sin mover stock ({@code InventoryUseCase.
     * setMinimumStock}): sin este método público, bajar el mínimo por debajo del saldo actual
     * no abriría alerta hasta el siguiente movimiento.
     */
    public void evaluateAlerts(Inventory inventory) {
        InventoryAlertType targetType;
        if (inventory.isOutOfStock()) {
            targetType = InventoryAlertType.OUT_OF_STOCK;
        } else if (inventory.isLowStock()) {
            targetType = InventoryAlertType.LOW_STOCK;
        } else {
            targetType = null;
        }

        Optional<InventoryAlert> openAlert = alertRepository.findOpenByInventoryId(inventory.getId());

        if (targetType == null) {
            openAlert.ifPresent(this::resolve);
            return;
        }

        if (openAlert.isPresent()) {
            if (openAlert.get().getAlertType() == targetType) {
                return;
            }
            resolve(openAlert.get());
        }

        InventoryAlert opened = InventoryAlert.open(
                inventory.getId(), targetType, inventory.getQuantity(), inventory.getMinimumStock());
        alertRepository.save(opened);
        log.info(() -> "Alerta abierta: tipo=%s, inventario=%s".formatted(targetType, inventory.getId()));
    }

    private void resolve(InventoryAlert alert) {
        alert.resolve();
        alertRepository.save(alert);
    }
}
