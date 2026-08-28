package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.SaleSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Sale;

import java.util.Optional;
import java.util.UUID;

public interface SaleRepositoryPort {

    Sale save(Sale sale);

    /** Carga la venta con sus líneas: el agregado nunca se devuelve incompleto. */
    Optional<Sale> findById(UUID id);

    boolean existsByBranchIdAndSaleNumber(UUID branchId, String saleNumber);

    PageResult<Sale> search(SaleSearchCriteria criteria, PageQuery pageQuery);
}
