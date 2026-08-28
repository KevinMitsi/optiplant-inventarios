package io.github.KevinMitsi.inventories.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un cambio de estado de una transferencia, ya ocurrido (ENTITIES.md §14.1).
 *
 * <p>El estado vigente vive en {@link Transfer#getStatus()}; esta es la fila de auditoría de
 * cómo se llegó hasta ahí, lo que sostiene los indicadores de cumplimiento logístico
 * (HU-36, HU-37). Es puramente un registro histórico: no se modifica una vez creado.
 */
public final class TransferStatusHistory {

    private final UUID id;
    private final UUID transferId;
    private final TransferStatus status;
    private final UUID changedBy;
    private final Instant changedAt;
    private final String notes;

    private TransferStatusHistory(UUID id, UUID transferId, TransferStatus status, UUID changedBy, Instant changedAt,
                                  String notes) {
        this.id = Objects.requireNonNull(id, "El identificador del histórico no puede ser nulo.");
        this.transferId = Objects.requireNonNull(transferId, "El histórico debe referenciar una transferencia.");
        this.status = Objects.requireNonNull(status, "El estado registrado es obligatorio.");
        this.changedBy = Objects.requireNonNull(changedBy, "El histórico debe registrar quién produjo el cambio.");
        this.changedAt = Objects.requireNonNull(changedAt, "La fecha del cambio es obligatoria.");
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public static TransferStatusHistory record(UUID transferId, TransferStatus status, UUID changedBy,
                                               String notes) {
        return new TransferStatusHistory(UUID.randomUUID(), transferId, status, changedBy, Instant.now(), notes);
    }

    public static TransferStatusHistory reconstitute(UUID id, UUID transferId, TransferStatus status,
                                                      UUID changedBy, Instant changedAt, String notes) {
        return new TransferStatusHistory(id, transferId, status, changedBy, changedAt, notes);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TransferStatusHistory history && id.equals(history.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
