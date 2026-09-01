package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.TransferStatusHistory;

import java.util.List;
import java.util.UUID;

public interface TransferStatusHistoryRepositoryPort {

    TransferStatusHistory save(TransferStatusHistory history);

    List<TransferStatusHistory> findByTransferId(UUID transferId);
}
