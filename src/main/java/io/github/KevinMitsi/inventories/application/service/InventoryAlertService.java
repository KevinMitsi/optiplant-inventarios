package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryAlertUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAlertRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta y cierre manual de alertas de reabastecimiento (funcionalidad adicional §34).
 *
 * <p>Abrirlas no es cosa de este servicio: lo hace {@link InventoryMovementPoster} en el
 * mismo instante en que un movimiento deja un saldo en o por debajo de su mínimo. Aquí solo
 * vive lo que inicia un usuario — descartarlas — y la resolución automática que ya ocurrió
 * queda simplemente para consulta.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class InventoryAlertService implements ManageInventoryAlertUseCase {

    private static final Logger log = LoggerFactory.getLogger(InventoryAlertService.class);

    private static final String ALERT = "la alerta de inventario";

    private final InventoryAlertRepositoryPort alertRepository;

    InventoryAlertService(InventoryAlertRepositoryPort alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public InventoryAlert resolveAlert(UUID alertId) {
        InventoryAlert alert = loadAlert(alertId);
        alert.resolve();
        InventoryAlert saved = alertRepository.save(alert);
        log.info("Alerta resuelta manualmente: id={}", saved.getId());
        return saved;
    }

    @Override
    public InventoryAlert dismissAlert(UUID alertId) {
        InventoryAlert alert = loadAlert(alertId);
        alert.dismiss();
        InventoryAlert saved = alertRepository.save(alert);
        log.info("Alerta descartada: id={}", saved.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<InventoryAlert> searchAlerts(InventoryAlertSearchCriteria criteria, PageQuery pageQuery) {
        return alertRepository.search(criteria, pageQuery);
    }

    private InventoryAlert loadAlert(UUID alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException(ALERT, alertId));
    }
}
