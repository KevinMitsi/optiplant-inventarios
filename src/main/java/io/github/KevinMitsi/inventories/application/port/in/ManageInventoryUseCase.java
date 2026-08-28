package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryEntryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryExitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetMinimumStockCommand;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;

public interface ManageInventoryUseCase {

    Inventory setMinimumStock(SetMinimumStockCommand command);

    InventoryMovement registerEntry(RegisterInventoryEntryCommand command);

    InventoryMovement registerExit(RegisterInventoryExitCommand command);
}
