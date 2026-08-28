package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.CarrierSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Carrier;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;

public interface QueryCarrierUseCase {

    Carrier getCarrierById(UUID carrierId);

    PageResult<Carrier> searchCarriers(CarrierSearchCriteria criteria, PageQuery pageQuery);
}
