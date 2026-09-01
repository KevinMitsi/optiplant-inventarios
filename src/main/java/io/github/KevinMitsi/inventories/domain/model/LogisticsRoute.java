package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ruta habitual entre dos sucursales, usada para clasificar transferencias por costo/tiempo
 * estimado y analizar su cumplimiento (EP-08, HU-36/37, ENTITIES.md §16.2).
 */
public final class LogisticsRoute {

    private static final int NAME_MAX_LENGTH = 150;

    private final UUID id;
    private final UUID organizationId;
    private final UUID originBranchId;
    private final UUID destinationBranchId;
    private final Instant createdAt;

    private String name;
    private int estimatedDurationMinutes;
    private Money estimatedCost;
    private short priority;
    private boolean active;
    private Instant updatedAt;

    private LogisticsRoute(UUID id, UUID organizationId, UUID originBranchId, UUID destinationBranchId, String name,
                           int estimatedDurationMinutes, Money estimatedCost, short priority, boolean active,
                           Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la ruta no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId, "La ruta debe pertenecer a una organización.");
        this.originBranchId = Objects.requireNonNull(originBranchId, "La ruta debe tener sucursal de origen.");
        this.destinationBranchId = Objects.requireNonNull(destinationBranchId,
                "La ruta debe tener sucursal de destino.");
        if (this.originBranchId.equals(this.destinationBranchId)) {
            throw new BusinessRuleViolationException("RN-07",
                    "El origen y el destino de una ruta logística deben ser sucursales distintas.");
        }
        this.name = normalizeName(name);
        this.estimatedDurationMinutes = requirePositiveDuration(estimatedDurationMinutes);
        this.estimatedCost = estimatedCost;
        this.priority = priority;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    public static LogisticsRoute create(UUID organizationId, UUID originBranchId, UUID destinationBranchId,
                                        String name, int estimatedDurationMinutes, Money estimatedCost,
                                        short priority) {
        Instant now = Instant.now();
        return new LogisticsRoute(UUID.randomUUID(), organizationId, originBranchId, destinationBranchId, name,
                estimatedDurationMinutes, estimatedCost, priority, true, now, now);
    }

    public static LogisticsRoute reconstitute(UUID id, UUID organizationId, UUID originBranchId,
                                              UUID destinationBranchId, String name, int estimatedDurationMinutes,
                                              Money estimatedCost, short priority, boolean active, Instant createdAt,
                                              Instant updatedAt) {
        return new LogisticsRoute(id, organizationId, originBranchId, destinationBranchId, name,
                estimatedDurationMinutes, estimatedCost, priority, active, createdAt, updatedAt);
    }

    public void updateDetails(String name, int estimatedDurationMinutes, Money estimatedCost, short priority) {
        this.name = normalizeName(name);
        this.estimatedDurationMinutes = requirePositiveDuration(estimatedDurationMinutes);
        this.estimatedCost = estimatedCost;
        this.priority = priority;
        touch();
    }

    public void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        touch();
    }

    public void activate() {
        if (active) {
            return;
        }
        active = true;
        touch();
    }

    /** Une un origen/destino con esta ruta: usado por {@code Transfer.assignLogistics}. */
    public boolean connects(UUID originBranchId, UUID destinationBranchId) {
        return this.originBranchId.equals(originBranchId) && this.destinationBranchId.equals(destinationBranchId);
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "El nombre no puede superar %d caracteres.".formatted(NAME_MAX_LENGTH));
        }
        return normalized;
    }

    private static int requirePositiveDuration(int estimatedDurationMinutes) {
        if (estimatedDurationMinutes <= 0) {
            throw new DomainValidationException("estimatedDurationMinutes",
                    "La duración estimada debe ser mayor que cero.");
        }
        return estimatedDurationMinutes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getOriginBranchId() {
        return originBranchId;
    }

    public UUID getDestinationBranchId() {
        return destinationBranchId;
    }

    public String getName() {
        return name;
    }

    public int getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public Money getEstimatedCost() {
        return estimatedCost;
    }

    public short getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
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
        return other instanceof LogisticsRoute route && id.equals(route.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LogisticsRoute[id=%s, origin=%s, destination=%s]".formatted(id, originBranchId, destinationBranchId);
    }
}
