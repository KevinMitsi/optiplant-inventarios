package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Documento formal de corrección de inventario (ENTITIES.md §18).
 *
 * <p>Existe junto al movimiento manual directo (HU-12/13) para el caso en que la corrección
 * afecta a varios productos a la vez y conviene dejar un único documento con responsable y
 * aprobador, en vez de N movimientos sueltos sin nada que los agrupe.
 *
 * <p>Es un agregado raíz de sus líneas, igual que {@link Product} lo es de sus presentaciones:
 * nace con todas sus líneas y no se pueden añadir después de aprobado. Aprobar es lo que
 * confirma el ajuste — a partir de ahí, quien orquesta la operación
 * ({@code InventoryMovementPoster}) postea un movimiento {@code ADJUSTMENT_IN}/
 * {@code ADJUSTMENT_OUT} por línea, referenciando este documento.
 */
public final class InventoryAdjustment {

    private static final int REASON_MAX_LENGTH = 250;

    private final UUID id;
    private final UUID branchId;
    private final UUID createdBy;
    private final String reason;
    private final List<InventoryAdjustmentItem> items;
    private final Instant createdAt;

    private UUID approvedBy;
    private Instant approvedAt;

    private InventoryAdjustment(UUID id,
                                UUID branchId,
                                UUID createdBy,
                                UUID approvedBy,
                                String reason,
                                List<InventoryAdjustmentItem> items,
                                Instant createdAt,
                                Instant approvedAt) {
        this.id = Objects.requireNonNull(id, "El identificador del ajuste no puede ser nulo.");
        this.branchId = Objects.requireNonNull(branchId, "El ajuste debe pertenecer a una sucursal.");
        this.createdBy = Objects.requireNonNull(createdBy, "El ajuste debe registrar quién lo creó.");
        this.reason = requireReason(reason);
        this.items = requireItems(items);
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria.");
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        if ((approvedBy == null) != (approvedAt == null)) {
            throw new DomainValidationException("approval",
                    "La aprobación del ajuste debe tener responsable y fecha, o ninguno de los dos.");
        }
    }

    public static InventoryAdjustment create(UUID branchId,
                                              UUID createdBy,
                                              String reason,
                                              List<InventoryAdjustmentItem> items) {
        return new InventoryAdjustment(UUID.randomUUID(), branchId, createdBy, null, reason, items,
                Instant.now(), null);
    }

    public static InventoryAdjustment reconstitute(UUID id,
                                                    UUID branchId,
                                                    UUID createdBy,
                                                    UUID approvedBy,
                                                    String reason,
                                                    List<InventoryAdjustmentItem> items,
                                                    Instant createdAt,
                                                    Instant approvedAt) {
        return new InventoryAdjustment(id, branchId, createdBy, approvedBy, reason, items, createdAt, approvedAt);
    }

    /**
     * Confirma el ajuste. A partir de aquí es inmutable: quien orquesta la operación debe
     * postear los movimientos correspondientes a cada línea inmediatamente después.
     */
    public void approve(UUID approvedBy) {
        Objects.requireNonNull(approvedBy, "Quien aprueba el ajuste debe identificarse.");
        if (isApproved()) {
            throw new BusinessRuleViolationException("RN-04",
                    "El ajuste ya fue aprobado y no puede confirmarse de nuevo.");
        }
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
    }

    public boolean isApproved() {
        return approvedAt != null;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainValidationException("reason", "El motivo del ajuste es obligatorio.");
        }
        String normalized = reason.trim();
        if (normalized.length() > REASON_MAX_LENGTH) {
            throw new DomainValidationException("reason",
                    "El motivo no puede superar %d caracteres.".formatted(REASON_MAX_LENGTH));
        }
        return normalized;
    }

    private static List<InventoryAdjustmentItem> requireItems(List<InventoryAdjustmentItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainValidationException("items", "El ajuste debe tener al menos una línea.");
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

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public String getReason() {
        return reason;
    }

    public List<InventoryAdjustmentItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof InventoryAdjustment adjustment && id.equals(adjustment.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "InventoryAdjustment[id=%s, branchId=%s, items=%d, approved=%s]"
                .formatted(id, branchId, items.size(), isApproved());
    }
}
