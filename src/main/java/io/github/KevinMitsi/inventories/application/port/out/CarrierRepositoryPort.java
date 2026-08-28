package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.CarrierSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Carrier;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface CarrierRepositoryPort {

    Carrier save(Carrier carrier);

    Optional<Carrier> findById(UUID id);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    boolean existsById(UUID id);

    PageResult<Carrier> search(CarrierSearchCriteria criteria, PageQuery pageQuery);
}
