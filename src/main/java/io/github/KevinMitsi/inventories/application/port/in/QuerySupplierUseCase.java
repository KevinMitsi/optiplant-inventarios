package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.SupplierSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Supplier;

import java.util.UUID;

public interface QuerySupplierUseCase {

    Supplier getSupplierById(UUID supplierId);

    PageResult<Supplier> searchSuppliers(SupplierSearchCriteria criteria, PageQuery pageQuery);
}
