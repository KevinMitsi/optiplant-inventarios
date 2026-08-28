package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryAlertUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.usecase.InventoryAlertUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class InventoryAlertService implements ManageInventoryAlertUseCase {

    private final InventoryAlertUseCase useCase;

    public InventoryAlertService(InventoryAlertUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public InventoryAlert resolveAlert(UUID alertId) {
        return useCase.resolveAlert(alertId);
    }

    @Override
    public InventoryAlert dismissAlert(UUID alertId) {
        return useCase.dismissAlert(alertId);
    }

    @Override
    public PageResult<InventoryAlert> searchAlerts(InventoryAlertSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchAlerts(criteria, pageQuery);
    }
}
