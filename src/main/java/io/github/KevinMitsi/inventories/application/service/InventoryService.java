package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryInventoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryEntryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryExitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetMinimumStockCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.InventorySearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.usecase.InventoryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class InventoryService implements ManageInventoryUseCase, QueryInventoryUseCase {

    private final InventoryUseCase useCase;

    public InventoryService(InventoryUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Inventory setMinimumStock(SetMinimumStockCommand command) {
        return useCase.setMinimumStock(command);
    }

    @Override
    public InventoryMovement registerEntry(RegisterInventoryEntryCommand command) {
        return useCase.registerEntry(command);
    }

    @Override
    public InventoryMovement registerExit(RegisterInventoryExitCommand command) {
        return useCase.registerExit(command);
    }

    @Override
    public Inventory getByBranchAndProduct(UUID branchId, UUID productId) {
        return useCase.getByBranchAndProduct(branchId, productId);
    }

    @Override
    public PageResult<Inventory> searchInventory(InventorySearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchInventory(criteria, pageQuery);
    }

    @Override
    public PageResult<InventoryMovement> getMovementHistory(UUID branchId, UUID productId, PageQuery pageQuery) {
        return useCase.getMovementHistory(branchId, productId, pageQuery);
    }
}
