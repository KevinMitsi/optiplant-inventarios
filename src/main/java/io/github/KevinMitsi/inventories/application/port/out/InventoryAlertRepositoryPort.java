package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.InventoryAlertSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface InventoryAlertRepositoryPort {

    InventoryAlert save(InventoryAlert alert);

    Optional<InventoryAlert> findById(UUID id);

    /**
     * La alerta abierta de un saldo, sea cual sea su tipo.
     *
     * <p>El índice único de la base solo impide duplicar el mismo tipo; que nunca haya dos
     * alertas abiertas de tipos distintos a la vez es una disciplina que garantiza
     * {@code InventoryMovementPoster}, resolviendo la anterior antes de abrir otra.
     */
    Optional<InventoryAlert> findOpenByInventoryId(UUID inventoryId);

    PageResult<InventoryAlert> search(InventoryAlertSearchCriteria criteria, PageQuery pageQuery);
}
