package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transferencia de mercancía entre dos sucursales, con sus líneas (EP-07, ENTITIES.md §13).
 *
 * <p>Máquina de estados de cinco pasos: solicitar ({@link #create}) → aprobar
 * ({@link #approve}) → preparar ({@link #startPreparation}) → despachar ({@link #dispatch})
 * → recibir ({@link #receive}). Como {@link PurchaseOrder} y {@link Sale}, este agregado solo
 * gobierna su propio ciclo de vida y el de sus líneas; no toca el inventario. Quien orquesta
 * ({@code TransferUseCase}) es responsable de postear {@code TRANSFER_OUT} al despachar
 * (validando RN-08 vía {@code InventoryMovementPoster}) y {@code TRANSFER_IN} al recibir
 * (RN-09), y de abrir un {@link TransferIssue} por cada línea que llegó incompleta (RN-10).
 *
 * <p>Asignar transportista/ruta ({@link #assignLogistics}) es tarea de logística (Fase 5):
 * solo procede antes de despachar, porque una vez en tránsito ya no tiene sentido cambiar
 * quién lo lleva ni por qué ruta.
 */
public final class Transfer {

    private static final int TRANSFER_NUMBER_MAX_LENGTH = 40;

    private final UUID id;
    private final String transferNumber;
    private final UUID originBranchId;
    private final UUID destinationBranchId;
    private final UUID requestedBy;
    private final TransferPriority priority;
    private final Instant requestedAt;
    private final String notes;
    private final List<TransferItem> items;
    private final Instant createdAt;

    private TransferStatus status;
    private UUID approvedBy;
    private Instant approvedAt;
    private UUID carrierId;
    private UUID routeId;
    private Instant shippedAt;
    private Instant estimatedArrivalAt;
    private Instant receivedAt;
    private Instant updatedAt;

    private Transfer(UUID id, String transferNumber, UUID originBranchId, UUID destinationBranchId,
                     UUID requestedBy, TransferStatus status, TransferPriority priority, Instant requestedAt,
                     UUID approvedBy, Instant approvedAt, UUID carrierId, UUID routeId, Instant shippedAt,
                     Instant estimatedArrivalAt, Instant receivedAt, String notes, List<TransferItem> items,
                     Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la transferencia no puede ser nulo.");
        this.transferNumber = requireTransferNumber(transferNumber);
        this.originBranchId = Objects.requireNonNull(originBranchId, "La transferencia debe tener sucursal de origen.");
        this.destinationBranchId = Objects.requireNonNull(destinationBranchId,
                "La transferencia debe tener sucursal de destino.");
        if (this.originBranchId.equals(this.destinationBranchId)) {
            throw new BusinessRuleViolationException("RN-07",
                    "El origen y el destino de una transferencia deben ser sucursales distintas.");
        }
        this.requestedBy = Objects.requireNonNull(requestedBy, "La transferencia debe registrar quién la solicitó.");
        this.status = Objects.requireNonNull(status, "El estado de la transferencia es obligatorio.");
        this.priority = Objects.requireNonNull(priority, "La prioridad de la transferencia es obligatoria.");
        this.requestedAt = Objects.requireNonNull(requestedAt, "La fecha de solicitud es obligatoria.");
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.carrierId = carrierId;
        this.routeId = routeId;
        this.shippedAt = shippedAt;
        this.estimatedArrivalAt = estimatedArrivalAt;
        this.receivedAt = receivedAt;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.items = requireItems(items);
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización es obligatoria.");
    }

    /** Nace {@code REQUESTED} (HU-27): la sucursal de origen pide reponer stock desde otra. */
    public static Transfer create(UUID originBranchId, UUID destinationBranchId, UUID requestedBy,
                                  String transferNumber, TransferPriority priority, String notes,
                                  List<TransferItem> items) {
        Instant now = Instant.now();
        return new Transfer(UUID.randomUUID(), transferNumber, originBranchId, destinationBranchId, requestedBy,
                TransferStatus.REQUESTED, priority, now, null, null, null, null, null, null, null, notes, items,
                now, now);
    }

    public static Transfer reconstitute(UUID id, String transferNumber, UUID originBranchId,
                                        UUID destinationBranchId, UUID requestedBy, TransferStatus status,
                                        TransferPriority priority, Instant requestedAt, UUID approvedBy,
                                        Instant approvedAt, UUID carrierId, UUID routeId, Instant shippedAt,
                                        Instant estimatedArrivalAt, Instant receivedAt, String notes,
                                        List<TransferItem> items, Instant createdAt, Instant updatedAt) {
        return new Transfer(id, transferNumber, originBranchId, destinationBranchId, requestedBy, status, priority,
                requestedAt, approvedBy, approvedAt, carrierId, routeId, shippedAt, estimatedArrivalAt, receivedAt,
                notes, items, createdAt, updatedAt);
    }

    /**
     * REQUESTED → APPROVED (HU-29). Cada línea queda aprobada por la cantidad indicada en
     * {@code approvedQuantities}, o por la cantidad solicitada si la línea no aparece ahí —
     * aprobar "tal cual" es el caso común, ajustar es la excepción.
     */
    public void approve(UUID approvedBy, Map<UUID, Quantity> approvedQuantities) {
        requireStatus(TransferStatus.REQUESTED, "aprobar");
        Objects.requireNonNull(approvedBy, "Quien aprueba debe quedar registrado.");

        for (TransferItem item : items) {
            Quantity quantity = approvedQuantities.getOrDefault(item.getId(), item.getRequestedQuantity());
            item.approve(quantity);
        }

        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
        this.status = TransferStatus.APPROVED;
        touch();
    }

    /**
     * Asigna transportista y ruta (Fase 5, Logística). Solo antes de despachar: una vez en
     * tránsito ya hay stock de origen comprometido bajo un plan de envío concreto.
     */
    public void assignLogistics(UUID carrierId, UUID routeId, Instant estimatedArrivalAt) {
        if (status != TransferStatus.REQUESTED && status != TransferStatus.APPROVED
                && status != TransferStatus.IN_PREPARATION) {
            throw new InvalidStateTransitionException("Transfer", status, "asignar transportista y ruta");
        }
        this.carrierId = Objects.requireNonNull(carrierId, "El transportista es obligatorio.");
        this.routeId = Objects.requireNonNull(routeId, "La ruta es obligatoria.");
        this.estimatedArrivalAt = estimatedArrivalAt;
        touch();
    }

    /** APPROVED → IN_PREPARATION: el origen empieza a alistar la mercancía. */
    public void startPreparation() {
        requireStatus(TransferStatus.APPROVED, "iniciar la preparación");
        this.status = TransferStatus.IN_PREPARATION;
        touch();
    }

    /**
     * IN_PREPARATION → IN_TRANSIT. Fija lo despachado por línea (por defecto, lo aprobado);
     * quien orquesta debe postear {@code TRANSFER_OUT} justo después, que es quien realmente
     * valida RN-08 contra el stock de origen.
     */
    public void dispatch(Map<UUID, Quantity> shippedQuantities) {
        requireStatus(TransferStatus.IN_PREPARATION, "despachar");

        for (TransferItem item : items) {
            Quantity quantity = shippedQuantities.getOrDefault(item.getId(), item.getApprovedQuantity());
            item.ship(quantity);
        }

        this.shippedAt = Instant.now();
        this.status = TransferStatus.IN_TRANSIT;
        touch();
    }

    /**
     * IN_TRANSIT → RECEIVED o PARTIALLY_RECEIVED (RN-09). Cada línea debe traer su cantidad
     * recibida explícita en {@code receivedQuantities} — a diferencia de aprobar/despachar,
     * aquí no hay valor por defecto razonable: lo que no llegó, llegó en cero.
     *
     * @return las líneas que quedaron con faltante ({@link TransferItem#missingQuantity()} > 0),
     *         para que quien orquesta abra una {@link TransferIssue} por cada una (RN-10)
     */
    public List<TransferItem> receive(Map<UUID, Quantity> receivedQuantities) {
        requireStatus(TransferStatus.IN_TRANSIT, "recibir");

        for (TransferItem item : items) {
            Quantity quantity = receivedQuantities.getOrDefault(item.getId(), Quantity.ZERO);
            item.receive(quantity);
        }

        this.receivedAt = Instant.now();
        boolean allComplete = items.stream().allMatch(TransferItem::isFullyReceived);
        this.status = allComplete ? TransferStatus.RECEIVED : TransferStatus.PARTIALLY_RECEIVED;
        touch();

        return items.stream().filter(item -> item.missingQuantity().isPositive()).toList();
    }

    /** Solo se puede cancelar antes de despachar: después ya hay stock de origen comprometido. */
    public void cancel() {
        if (status != TransferStatus.REQUESTED && status != TransferStatus.APPROVED
                && status != TransferStatus.IN_PREPARATION) {
            throw new InvalidStateTransitionException("Transfer", status, "cancelar");
        }
        this.status = TransferStatus.CANCELLED;
        touch();
    }

    /** PARTIALLY_RECEIVED → CLOSED, una vez que todas sus incidencias quedaron resueltas (HU-33). */
    public void close() {
        requireStatus(TransferStatus.PARTIALLY_RECEIVED, "cerrar");
        this.status = TransferStatus.CLOSED;
        touch();
    }

    public Optional<TransferItem> findItemById(UUID itemId) {
        return items.stream().filter(item -> item.getId().equals(itemId)).findFirst();
    }

    private void requireStatus(TransferStatus required, String operation) {
        if (status != required) {
            throw new InvalidStateTransitionException("Transfer", status, operation);
        }
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private static String requireTransferNumber(String transferNumber) {
        if (transferNumber == null || transferNumber.isBlank()) {
            throw new DomainValidationException("transferNumber", "El número de transferencia es obligatorio.");
        }
        String normalized = transferNumber.trim();
        if (normalized.length() > TRANSFER_NUMBER_MAX_LENGTH) {
            throw new DomainValidationException("transferNumber",
                    "El número de transferencia no puede superar %d caracteres.".formatted(TRANSFER_NUMBER_MAX_LENGTH));
        }
        return normalized;
    }

    private static List<TransferItem> requireItems(List<TransferItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainValidationException("items", "La transferencia debe tener al menos una línea.");
        }
        return new ArrayList<>(items);
    }

    public UUID getId() {
        return id;
    }

    public String getTransferNumber() {
        return transferNumber;
    }

    public UUID getOriginBranchId() {
        return originBranchId;
    }

    public UUID getDestinationBranchId() {
        return destinationBranchId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public TransferPriority getPriority() {
        return priority;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public UUID getCarrierId() {
        return carrierId;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getEstimatedArrivalAt() {
        return estimatedArrivalAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getNotes() {
        return notes;
    }

    public List<TransferItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Transfer transfer && id.equals(transfer.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Transfer[id=%s, number=%s, status=%s]".formatted(id, transferNumber, status);
    }
}
