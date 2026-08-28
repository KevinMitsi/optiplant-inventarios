package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateCarrierCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateCarrierCommand;
import io.github.KevinMitsi.inventories.domain.model.Carrier;

import java.util.UUID;

public interface ManageCarrierUseCase {

    Carrier createCarrier(CreateCarrierCommand command);

    Carrier updateCarrier(UpdateCarrierCommand command);

    Carrier deactivateCarrier(UUID carrierId);

    Carrier activateCarrier(UUID carrierId);
}
