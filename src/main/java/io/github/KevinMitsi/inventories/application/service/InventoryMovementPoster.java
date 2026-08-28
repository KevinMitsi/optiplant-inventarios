package io.github.KevinMitsi.inventories.application.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Único punto del sistema que aplica un movimiento de inventario (RN-04, DBD-03).
 *
 * <p>Compras, ventas, transferencias y ajustes formales son clientes de este componente:
 * ninguno vuelve a tocar {@code Inventory.quantity} directamente. Concentrar aquí la escritura
 * es lo que hace posible garantizar, con una sola implementación, el invariante central del
 * dominio — <b>ningún saldo cambia sin una fila en {@code inventory_movement} que lo
 * explique</b> — en lugar de confiar en que cada módulo nuevo lo respete por su cuenta.
 *
 * <p>No es un caso de uso: no lo expone directamente ningún controlador, ni tiene puerto
 * {@code in} propio. Vive en la capa de aplicación como colaborador interno de los servicios
 * que sí lo son.
 *
 * <p>Cada posteo hace tres cosas en la misma transacción: (1) actualiza el saldo —y, si es
 * una compra, recalcula el costo promedio ponderado (RF-23)—, (2) dejar constancia del cambio
 * en el histórico inmutable, y (3) evaluar si corresponde abrir, mantener o resolver una
 * alerta de reabastecimiento (funcionalidad adicional §34). Un fallo en cualquier paso
 * revierte los tres, porque el servicio que orquesta la operación de negocio está anotado con
 * {@code @Transactional(rollbackFor = Exception.class)}.
 */
@Service
class InventoryMovementPoster {

    private static final Logger log = LoggerFactory.getLogger(InventoryMovementPoster.class);

    private final InventoryRepositoryPort inventoryRepository;
    private final InventoryMovementRepositoryPort movementRepository;
    private final InventoryAlertRepositoryPort alertRepository;

    InventoryMovementPoster(InventoryRepositoryPort inventoryRepository,
                            InventoryMovementRepositoryPort movementRepository,
                            InventoryAlertRepositoryPort alertRepository) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.alertRepository = alertRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    InventoryMovement post(PostInventoryMovementCommand command) {
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

        log.info("Movimiento posteado: tipo={}, sucursal={}, producto={}, cantidad={}",
                type, saved.getBranchId(), saved.getProductId(), quantity);
        return savedMovement;
    }

    /** Aplica el movimiento sobre el saldo y devuelve el costo unitario a registrar, si aplica. */
    private Money applyToInventory(Inventory inventory,
                                   InventoryMovementType type,
                                   Quantity quantity,
                                   PostInventoryMovementCommand command) {
        if (type.isExit()) {
            requireSufficientStock(inventory, quantity, command.productSku());
            inventory.decrease(quantity);
            return null;
        }

        if (type == InventoryMovementType.PURCHASE_IN) {
            Money unitCost = Money.of(command.unitCost());
            inventory.receivePurchase(quantity, unitCost);
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
     * Abre, mantiene o resuelve la alerta de reabastecimiento del saldo (RF-16, §34).
     *
     * <p>Como mucho una alerta permanece abierta por saldo: si cambia el tipo que corresponde
     * —de {@code LOW_STOCK} a {@code OUT_OF_STOCK}, por ejemplo— la anterior se resuelve antes
     * de abrir la nueva, nunca conviven las dos.
     */
    private void evaluateAlerts(Inventory inventory) {
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
        log.info("Alerta abierta: tipo={}, inventario={}", targetType, inventory.getId());
    }

    private void resolve(InventoryAlert alert) {
        alert.resolve();
        alertRepository.save(alert);
    }
}
