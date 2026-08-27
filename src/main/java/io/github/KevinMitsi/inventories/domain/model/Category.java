package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Clasificación de productos dentro de una organización. */
public final class Category {

    private static final int CODE_MAX_LENGTH = 30;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 250;

    private final UUID id;
    private final UUID organizationId;
    private final String code;
    private final Instant createdAt;

    private String name;
    private String description;
    private boolean active;
    private Instant updatedAt;

    private Category(UUID id,
                     UUID organizationId,
                     String code,
                     String name,
                     String description,
                     boolean active,
                     Instant createdAt,
                     Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la categoría no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId,
                "La categoría debe pertenecer a una organización.");
        this.code = requireCode(code);
        this.name = requireName(name);
        this.description = normalizeOptional(description);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    public static Category create(UUID organizationId, String code, String name, String description) {
        Instant now = Instant.now();
        return new Category(UUID.randomUUID(), organizationId, code, name, description, true, now, now);
    }

    public static Category reconstitute(UUID id,
                                        UUID organizationId,
                                        String code,
                                        String name,
                                        String description,
                                        boolean active,
                                        Instant createdAt,
                                        Instant updatedAt) {
        return new Category(id, organizationId, code, name, description, active, createdAt, updatedAt);
    }

    public void updateDetails(String name, String description) {
        this.name = requireName(name);
        this.description = normalizeOptional(description);
        touch();
    }

    /** Baja lógica: los productos ya clasificados seguirían apuntando a ella. */
    public void deactivate() {
        if (!active) {
            return;
        }
        this.active = false;
        touch();
    }

    public void activate() {
        if (active) {
            return;
        }
        this.active = true;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DomainValidationException("code", "El código de la categoría es obligatorio.");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > CODE_MAX_LENGTH) {
            throw new DomainValidationException("code",
                    "El código no puede superar %d caracteres.".formatted(CODE_MAX_LENGTH));
        }
        return normalized;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name", "El nombre de la categoría es obligatorio.");
        }
        String normalized = name.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "El nombre no puede superar %d caracteres.".formatted(NAME_MAX_LENGTH));
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > DESCRIPTION_MAX_LENGTH) {
            throw new DomainValidationException("description",
                    "La descripción no puede superar %d caracteres.".formatted(DESCRIPTION_MAX_LENGTH));
        }
        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
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
        return other instanceof Category category && id.equals(category.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Category[id=%s, code=%s, name=%s]".formatted(id, code, name);
    }
}
