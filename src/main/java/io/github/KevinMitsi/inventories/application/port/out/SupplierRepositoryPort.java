package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.SupplierSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Supplier;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepositoryPort {

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(UUID id);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    boolean existsById(UUID id);

    PageResult<Supplier> search(SupplierSearchCriteria criteria, PageQuery pageQuery);
}
