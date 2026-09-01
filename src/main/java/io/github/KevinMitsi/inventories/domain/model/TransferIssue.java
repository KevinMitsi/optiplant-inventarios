package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Faltante o avería detectado al recibir una línea de transferencia (ENTITIES.md §15.3).
 *
 * <p>Agregado independiente de {@link Transfer} y no una colección suya: se resuelve en su
 * propio momento, normalmente por otra persona (quien recibe reporta, un responsable
 * distinto puede resolver) y se consulta como bandeja propia ("incidencias pendientes"),
 * no como parte del documento de la transferencia. Nunca se resuelve tocando el inventario
 * destino directamente (RN-10) — resolver aquí es dejar constancia de qué se decidió hacer,
 * no ejecutarlo automáticamente (ver {@link TransferIssueResolution}).
 */
public final class TransferIssue {

    private final UUID id;
    private final UUID transferItemId;
    private final TransferIssueType issueType;
    private final Quantity quantity;
    private final String description;
    private final UUID reportedBy;
    private final Instant reportedAt;

    private TransferIssueResolution resolutionType;
    private UUID resolvedBy;
    private Instant resolvedAt;

    private TransferIssue(UUID id, UUID transferItemId, TransferIssueType issueType, Quantity quantity,
                          String description, UUID reportedBy, Instant reportedAt,
                          TransferIssueResolution resolutionType, UUID resolvedBy, Instant resolvedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la incidencia no puede ser nulo.");
        this.transferItemId = Objects.requireNonNull(transferItemId, "La incidencia debe referenciar una línea.");
        this.issueType = Objects.requireNonNull(issueType, "El tipo de incidencia es obligatorio.");
        this.quantity = requirePositive(quantity);
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.reportedBy = Objects.requireNonNull(reportedBy, "La incidencia debe registrar quién la reportó.");
        this.reportedAt = Objects.requireNonNull(reportedAt, "La fecha de reporte es obligatoria.");
        this.resolutionType = resolutionType;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
    }

    /** Se abre automáticamente al recibir menos de lo despachado (RN-10). */
    public static TransferIssue report(UUID transferItemId, TransferIssueType issueType, Quantity quantity,
                                       String description, UUID reportedBy) {
        return new TransferIssue(UUID.randomUUID(), transferItemId, issueType, quantity, description, reportedBy,
                Instant.now(), null, null, null);
    }

    public static TransferIssue reconstitute(UUID id, UUID transferItemId, TransferIssueType issueType,
                                             Quantity quantity, String description, UUID reportedBy,
                                             Instant reportedAt, TransferIssueResolution resolutionType,
                                             UUID resolvedBy, Instant resolvedAt) {
        return new TransferIssue(id, transferItemId, issueType, quantity, description, reportedBy, reportedAt,
                resolutionType, resolvedBy, resolvedAt);
    }

    /** Deja constancia de cómo se resolvió (HU-33). No puede resolverse dos veces. */
    public void resolve(TransferIssueResolution resolutionType, UUID resolvedBy) {
        if (isResolved()) {
            throw new BusinessRuleViolationException("RN-10", "Esta incidencia ya fue resuelta.");
        }
        this.resolutionType = Objects.requireNonNull(resolutionType, "El tipo de resolución es obligatorio.");
        this.resolvedBy = Objects.requireNonNull(resolvedBy, "Quien resuelve debe quedar registrado.");
        this.resolvedAt = Instant.now();
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }

    private static Quantity requirePositive(Quantity quantity) {
        Objects.requireNonNull(quantity, "La cantidad de la incidencia es obligatoria.");
        if (!quantity.isPositive()) {
            throw new DomainValidationException("quantity", "La cantidad de la incidencia debe ser mayor que cero.");
        }
        return quantity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransferItemId() {
        return transferItemId;
    }

    public TransferIssueType getIssueType() {
        return issueType;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public String getDescription() {
        return description;
    }

    public UUID getReportedBy() {
        return reportedBy;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public TransferIssueResolution getResolutionType() {
        return resolutionType;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TransferIssue issue && id.equals(issue.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
