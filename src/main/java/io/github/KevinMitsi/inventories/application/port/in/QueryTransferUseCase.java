package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.TransferSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Transfer;

import java.util.UUID;

public interface QueryTransferUseCase {

    Transfer getTransferById(UUID transferId);

    PageResult<Transfer> searchTransfers(TransferSearchCriteria criteria, PageQuery pageQuery);
}
