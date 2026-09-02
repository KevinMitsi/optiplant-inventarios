package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryAlertUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.result.InventoryAlertDetail;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAlertRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryRepositoryPort;
import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;
import java.util.logging.Logger;

@AuditedUseCase
public class InventoryAlertUseCase implements ManageInventoryAlertUseCase {

    private static final Logger log = Logger.getLogger(InventoryAlertUseCase.class.getName());

    private static final String ALERT = "la alerta de inventario";
    private static final String INVENTORY = "el inventario";

    private final InventoryAlertRepositoryPort alertRepository;
    private final InventoryRepositoryPort inventoryRepository;

    public InventoryAlertUseCase(InventoryAlertRepositoryPort alertRepository,
                                 InventoryRepositoryPort inventoryRepository) {
        this.alertRepository = alertRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public InventoryAlertDetail resolveAlert(UUID alertId) {
        InventoryAlert alert = loadAlert(alertId);
        alert.resolve();
        InventoryAlert saved = alertRepository.save(alert);
        log.info(() -> "Alerta resuelta manualmente: id=%s".formatted(saved.getId()));
        return toDetail(saved);
    }

    @Override
    public InventoryAlertDetail dismissAlert(UUID alertId) {
        InventoryAlert alert = loadAlert(alertId);
        alert.dismiss();
        InventoryAlert saved = alertRepository.save(alert);
        log.info(() -> "Alerta descartada: id=%s".formatted(saved.getId()));
        return toDetail(saved);
    }

    @Override
    public PageResult<InventoryAlertDetail> searchAlerts(InventoryAlertSearchCriteria criteria, PageQuery pageQuery) {
        return alertRepository.search(criteria, pageQuery).map(this::toDetail);
    }

    private InventoryAlert loadAlert(UUID alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException(ALERT, alertId));
    }

    private InventoryAlertDetail toDetail(InventoryAlert alert) {
        Inventory inventory = inventoryRepository.findById(alert.getInventoryId())
                .orElseThrow(() -> new ResourceNotFoundException(INVENTORY, alert.getInventoryId()));
        return new InventoryAlertDetail(alert, inventory.getBranchId(), inventory.getProductId());
    }
}
