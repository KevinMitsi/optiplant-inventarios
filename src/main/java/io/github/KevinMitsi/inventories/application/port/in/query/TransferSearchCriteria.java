package io.github.KevinMitsi.inventories.application.port.in.query;

import io.github.KevinMitsi.inventories.domain.model.TransferStatus;

import java.util.UUID;

/**
 * Transferencias en curso vistas desde cada extremo (RF-46, HU-35, HU-41): {@code branchId} se
 * compara contra origen y destino a la vez, porque una sucursal necesita ver tanto lo que pidió
 * como lo que le están por enviar.
 */
public record TransferSearchCriteria(UUID branchId, TransferStatus status) {

    public static TransferSearchCriteria ofBranch(UUID branchId) {
        return new TransferSearchCriteria(branchId, null);
    }
}
