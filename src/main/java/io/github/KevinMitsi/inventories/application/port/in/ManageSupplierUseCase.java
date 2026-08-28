package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateSupplierCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateSupplierCommand;
import io.github.KevinMitsi.inventories.domain.model.Supplier;

import java.util.UUID;

public interface ManageSupplierUseCase {

    Supplier createSupplier(CreateSupplierCommand command);

    Supplier updateSupplier(UpdateSupplierCommand command);

    Supplier deactivateSupplier(UUID supplierId);

    Supplier activateSupplier(UUID supplierId);
}
