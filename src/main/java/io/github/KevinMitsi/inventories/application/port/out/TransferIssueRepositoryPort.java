package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.TransferIssue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferIssueRepositoryPort {

    TransferIssue save(TransferIssue issue);

    Optional<TransferIssue> findById(UUID id);

    List<TransferIssue> findByTransferItemIdIn(List<UUID> transferItemIds);

    /**
     * Si queda al menos una incidencia sin resolver entre esas líneas. Se consulta por el
     * conjunto de líneas de una transferencia concreta, nunca en toda la tabla — evitando
     * que {@code TransferIssue} necesite conocer a qué transferencia pertenece cada línea.
     */
    boolean existsUnresolvedByTransferItemIdIn(List<UUID> transferItemIds);
}
