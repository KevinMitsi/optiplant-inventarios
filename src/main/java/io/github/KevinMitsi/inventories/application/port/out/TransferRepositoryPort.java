package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.TransferSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Transfer;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepositoryPort {

    Transfer save(Transfer transfer);

    /** Carga la transferencia con sus líneas: el agregado nunca se devuelve incompleto. */
    Optional<Transfer> findById(UUID id);

    boolean existsByTransferNumber(String transferNumber);

    PageResult<Transfer> search(TransferSearchCriteria criteria, PageQuery pageQuery);
}
