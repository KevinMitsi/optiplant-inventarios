package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.in.result.InventoryAlertDetail;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;

public interface ManageInventoryAlertUseCase {

    InventoryAlertDetail resolveAlert(UUID alertId);

    InventoryAlertDetail dismissAlert(UUID alertId);

    PageResult<InventoryAlertDetail> searchAlerts(InventoryAlertSearchCriteria criteria, PageQuery pageQuery);
}
