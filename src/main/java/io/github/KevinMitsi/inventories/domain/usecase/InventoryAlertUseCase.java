package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryAlertUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAlertRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;
import java.util.logging.Logger;

public class InventoryAlertUseCase implements ManageInventoryAlertUseCase {

    private static final Logger log = Logger.getLogger(InventoryAlertUseCase.class.getName());

    private static final String ALERT = "la alerta de inventario";

    private final InventoryAlertRepositoryPort alertRepository;

    public InventoryAlertUseCase(InventoryAlertRepositoryPort alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public InventoryAlert resolveAlert(UUID alertId) {
        InventoryAlert alert = loadAlert(alertId);
        alert.resolve();
        InventoryAlert saved = alertRepository.save(alert);
        log.info(() -> "Alerta resuelta manualmente: id=%s".formatted(saved.getId()));
        return saved;
    }

    @Override
    public InventoryAlert dismissAlert(UUID alertId) {
        InventoryAlert alert = loadAlert(alertId);
        alert.dismiss();
        InventoryAlert saved = alertRepository.save(alert);
        log.info(() -> "Alerta descartada: id=%s".formatted(saved.getId()));
        return saved;
    }

    @Override
    public PageResult<InventoryAlert> searchAlerts(InventoryAlertSearchCriteria criteria, PageQuery pageQuery) {
        return alertRepository.search(criteria, pageQuery);
    }

    private InventoryAlert loadAlert(UUID alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException(ALERT, alertId));
    }
}
