package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.CreatePriceListCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetProductPriceCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdatePriceListCommand;
import io.github.KevinMitsi.inventories.domain.model.PriceList;
import io.github.KevinMitsi.inventories.domain.model.ProductPrice;

import java.util.UUID;

public interface ManagePriceListUseCase {

    PriceList createPriceList(CreatePriceListCommand command);

    PriceList updatePriceList(UpdatePriceListCommand command);

    PriceList deactivatePriceList(UUID priceListId);

    PriceList activatePriceList(UUID priceListId);

    ProductPrice setProductPrice(SetProductPriceCommand command);
}
