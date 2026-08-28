package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orden de compra a un proveedor, con sus líneas (EP-05, ENTITIES.md §10).
 *
 * <p>Es el agregado raíz de sus líneas: recibir mercancía es una operación que cambia tanto
 * una línea (su {@code receivedQuantity}) como potencialmente la cabecera (su {@code status}
 * pasa a {@code PARTIALLY_RECEIVED} o {@code RECEIVED}), y solo el agregado puede mantener
 * esa coherencia. Quien orquesta la recepción —{@code PurchaseOrderService}— es responsable
 * de, además, postear el movimiento {@code PURCHASE_IN} correspondiente a través de
 * {@code InventoryMovementPoster}; este agregado no conoce el inventario.
 */
public final class PurchaseOrder {

    private static final int ORDER_NUMBER_MAX_LENGTH = 40;

    private final UUID id;
    private final UUID branchId;
    private final UUID supplierId;
    private final UUID createdBy;
    private final String orderNumber;
    private final LocalDate orderDate;
    private final int paymentTermDays;
    private final String notes;
    private final List<PurchaseOrderItem> items;
    private final Instant createdAt;

    private PurchaseOrderStatus status;
    private Instant updatedAt;

    private PurchaseOrder(UUID id, UUID branchId, UUID supplierId, UUID createdBy, PurchaseOrderStatus status,
                          String orderNumber, LocalDate orderDate, int paymentTermDays, String notes,
                          List<PurchaseOrderItem> items, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la orden no puede ser nulo.");
        this.branchId = Objects.requireNonNull(branchId, "La orden debe pertenecer a una sucursal.");
        this.supplierId = Objects.requireNonNull(supplierId, "La orden debe referenciar un proveedor.");
        this.createdBy = Objects.requireNonNull(createdBy, "La orden debe registrar quién la creó.");
        this.status = Objects.requireNonNull(status, "El estado de la orden es obligatorio.");
        this.orderNumber = requireOrderNumber(orderNumber);
        this.orderDate = Objects.requireNonNull(orderDate, "La fecha de la orden es obligatoria.");
        if (paymentTermDays < 0) {
            throw new DomainValidationException("paymentTermDays", "El plazo de pago no puede ser negativo.");
        }
        this.paymentTermDays = paymentTermDays;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.items = requireItems(items);
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización es obligatoria.");
    }

    public static PurchaseOrder create(UUID branchId, UUID supplierId, UUID createdBy, String orderNumber,
                                       LocalDate orderDate, int paymentTermDays, String notes,
                                       List<PurchaseOrderItem> items) {
        Instant now = Instant.now();
        return new PurchaseOrder(UUID.randomUUID(), branchId, supplierId, createdBy, PurchaseOrderStatus.DRAFT,
                orderNumber, orderDate, paymentTermDays, notes, items, now, now);
    }

    public static PurchaseOrder reconstitute(UUID id, UUID branchId, UUID supplierId, UUID createdBy,
                                             PurchaseOrderStatus status, String orderNumber, LocalDate orderDate,
                                             int paymentTermDays, String notes, List<PurchaseOrderItem> items,
                                             Instant createdAt, Instant updatedAt) {
        return new PurchaseOrder(id, branchId, supplierId, createdBy, status, orderNumber, orderDate,
                paymentTermDays, notes, items, createdAt, updatedAt);
    }

    /** DRAFT → CONFIRMED (HU-17/HU-18): a partir de aquí puede empezar a recibirse mercancía. */
    public void confirm() {
        requireStatus(PurchaseOrderStatus.DRAFT, "confirmar");
        this.status = PurchaseOrderStatus.CONFIRMED;
        touch();
    }

    /** Solo se puede cancelar antes de que llegue nada: cancelar con recepciones ya no tiene sentido. */
    public void cancel() {
        if (status != PurchaseOrderStatus.DRAFT && status != PurchaseOrderStatus.CONFIRMED) {
            throw new InvalidStateTransitionException("PurchaseOrder", status, "cancelar");
        }
        this.status = PurchaseOrderStatus.CANCELLED;
        touch();
    }

    /**
     * Registra la recepción de una línea y recalcula el estado de la cabecera (HU-19, RF-21).
     *
     * <p>No toca el inventario: eso lo hace quien orquesta la operación, después de que esta
     * llamada confirme que la línea admitía la cantidad recibida.
     */
    public PurchaseOrderItem receiveItem(UUID itemId, Quantity quantityReceivedNow) {
        if (status != PurchaseOrderStatus.CONFIRMED && status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new InvalidStateTransitionException("PurchaseOrder", status, "recibir mercancía");
        }

        PurchaseOrderItem item = requireItem(itemId);
        item.receive(quantityReceivedNow);

        this.status = items.stream().allMatch(PurchaseOrderItem::isFullyReceived)
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;
        touch();
        return item;
    }

    public Optional<PurchaseOrderItem> findItemById(UUID itemId) {
        return items.stream().filter(item -> item.getId().equals(itemId)).findFirst();
    }

    private PurchaseOrderItem requireItem(UUID itemId) {
        return findItemById(itemId).orElseThrow(() -> new DomainValidationException("itemId",
                "La línea indicada no pertenece a esta orden de compra."));
    }

    private void requireStatus(PurchaseOrderStatus required, String operation) {
        if (status != required) {
            throw new InvalidStateTransitionException("PurchaseOrder", status, operation);
        }
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private static String requireOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new DomainValidationException("orderNumber", "El número de orden es obligatorio.");
        }
        String normalized = orderNumber.trim();
        if (normalized.length() > ORDER_NUMBER_MAX_LENGTH) {
            throw new DomainValidationException("orderNumber",
                    "El número de orden no puede superar %d caracteres.".formatted(ORDER_NUMBER_MAX_LENGTH));
        }
        return normalized;
    }

    private static List<PurchaseOrderItem> requireItems(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainValidationException("items", "La orden de compra debe tener al menos una línea.");
        }
        return new ArrayList<>(items);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public int getPaymentTermDays() {
        return paymentTermDays;
    }

    public String getNotes() {
        return notes;
    }

    public List<PurchaseOrderItem> getItems() {
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
        return other instanceof PurchaseOrder order && id.equals(order.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "PurchaseOrder[id=%s, number=%s, status=%s]".formatted(id, orderNumber, status);
    }
}
