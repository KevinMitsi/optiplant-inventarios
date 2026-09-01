package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;

public interface ManageInventoryAlertUseCase {

    InventoryAlert resolveAlert(UUID alertId);

    InventoryAlert dismissAlert(UUID alertId);

    PageResult<InventoryAlert> searchAlerts(InventoryAlertSearchCriteria criteria, PageQuery pageQuery);
}
