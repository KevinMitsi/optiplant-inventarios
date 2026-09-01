package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.PriceListSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PriceList;

import java.util.Optional;
import java.util.UUID;

public interface PriceListRepositoryPort {

    PriceList save(PriceList priceList);

    Optional<PriceList> findById(UUID id);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    boolean existsById(UUID id);

    PageResult<PriceList> search(PriceListSearchCriteria criteria, PageQuery pageQuery);
}
