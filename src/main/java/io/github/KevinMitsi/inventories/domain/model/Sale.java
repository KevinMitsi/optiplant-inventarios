package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Venta a un cliente, con sus líneas (EP-06, ENTITIES.md §11).
 *
 * <p>Como {@link io.github.KevinMitsi.inventories.domain.model.PurchaseOrder}, este agregado
 * solo gobierna su propio ciclo de vida ({@code status}); no descuenta inventario por sí
 * mismo. Confirmar y cancelar son operaciones de dos pasos que orquesta
 * {@code SaleService}: primero la transición aquí, después el movimiento correspondiente
 * ({@code SALE_OUT} al confirmar, {@code RETURN_IN} compensatorio si se cancela una venta ya
 * confirmada) a través de {@code InventoryMovementPoster} — que es, de paso, quien valida
 * RN-03 al negarse a descontar más de lo disponible.
 */
public final class Sale {

    private static final int SALE_NUMBER_MAX_LENGTH = 40;

    private final UUID id;
    private final UUID branchId;
    private final UUID createdBy;
    private final UUID priceListId;
    private final String saleNumber;
    private final Instant saleDate;
    private final String notes;
    private final List<SaleItem> items;
    private final Instant createdAt;

    private SaleStatus status;

    private Sale(UUID id, UUID branchId, UUID createdBy, UUID priceListId, SaleStatus status, String saleNumber,
                Instant saleDate, String notes, List<SaleItem> items, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la venta no puede ser nulo.");
        this.branchId = Objects.requireNonNull(branchId, "La venta debe pertenecer a una sucursal.");
        this.createdBy = Objects.requireNonNull(createdBy, "La venta debe registrar quién la creó.");
        this.priceListId = priceListId;
        this.status = Objects.requireNonNull(status, "El estado de la venta es obligatorio.");
        this.saleNumber = requireSaleNumber(saleNumber);
        this.saleDate = Objects.requireNonNull(saleDate, "La fecha de la venta es obligatoria.");
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.items = requireItems(items);
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria.");
    }

    public static Sale create(UUID branchId, UUID createdBy, UUID priceListId, String saleNumber, Instant saleDate,
                              String notes, List<SaleItem> items) {
        return new Sale(UUID.randomUUID(), branchId, createdBy, priceListId, SaleStatus.DRAFT, saleNumber,
                saleDate, notes, items, Instant.now());
    }

    public static Sale reconstitute(UUID id, UUID branchId, UUID createdBy, UUID priceListId, SaleStatus status,
                                    String saleNumber, Instant saleDate, String notes, List<SaleItem> items,
                                    Instant createdAt) {
        return new Sale(id, branchId, createdBy, priceListId, status, saleNumber, saleDate, notes, items, createdAt);
    }

    /** DRAFT → CONFIRMED (HU-22). Quien orquesta debe descontar inventario justo después. */
    public void confirm() {
        if (status != SaleStatus.DRAFT) {
            throw new InvalidStateTransitionException("Sale", status, "confirmar");
        }
        this.status = SaleStatus.CONFIRMED;
    }

    /**
     * Cancela la venta. Si estaba confirmada, quien orquesta debe restituir el inventario con
     * un movimiento compensatorio inmediatamente después — la venta cancelada no debe
     * desaparecer del histórico, solo dejar de tener efecto (§11.1).
     */
    public void cancel() {
        if (status == SaleStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Sale", status, "cancelar");
        }
        this.status = SaleStatus.CANCELLED;
    }

    public boolean wasConfirmed() {
        return status == SaleStatus.CANCELLED || status == SaleStatus.CONFIRMED;
    }

    /** Total de la venta: suma de los subtotales netos de cada línea. */
    public Money total() {
        return items.stream().map(SaleItem::subtotal).reduce(Money.ZERO, Money::add);
    }

    private static String requireSaleNumber(String saleNumber) {
        if (saleNumber == null || saleNumber.isBlank()) {
            throw new DomainValidationException("saleNumber", "El número de venta es obligatorio.");
        }
        String normalized = saleNumber.trim();
        if (normalized.length() > SALE_NUMBER_MAX_LENGTH) {
            throw new DomainValidationException("saleNumber",
                    "El número de venta no puede superar %d caracteres.".formatted(SALE_NUMBER_MAX_LENGTH));
        }
        return normalized;
    }

    private static List<SaleItem> requireItems(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainValidationException("items", "La venta debe tener al menos una línea.");
        }
        return new ArrayList<>(items);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getPriceListId() {
        return priceListId;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public String getSaleNumber() {
        return saleNumber;
    }

    public Instant getSaleDate() {
        return saleDate;
    }

    public String getNotes() {
        return notes;
    }

    public List<SaleItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Sale sale && id.equals(sale.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Sale[id=%s, number=%s, status=%s]".formatted(id, saleNumber, status);
    }
}
