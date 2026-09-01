package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transportista contratado para mover mercancía entre sucursales (EP-08, ENTITIES.md §16.1).
 *
 * <p>Igual patrón de baja lógica que {@link Supplier}: aparece referenciado desde
 * transferencias históricas ({@code Transfer.carrierId}), así que desactivar reemplaza a
 * eliminar.
 */
public final class Carrier {

    private static final int CODE_MAX_LENGTH = 30;
    private static final int NAME_MAX_LENGTH = 150;

    private final UUID id;
    private final UUID organizationId;
    private final String code;
    private final Instant createdAt;

    private String name;
    private String phone;
    private String email;
    private boolean active;
    private Instant updatedAt;

    private Carrier(UUID id, UUID organizationId, String code, String name, String phone, String email,
                    boolean active, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador del transportista no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId,
                "El transportista debe pertenecer a una organización.");
        this.code = requireCode(code);
        this.name = requireName(name);
        this.phone = normalize(phone, 30);
        this.email = normalize(email, 254);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    public static Carrier create(UUID organizationId, String code, String name, String phone, String email) {
        Instant now = Instant.now();
        return new Carrier(UUID.randomUUID(), organizationId, code, name, phone, email, true, now, now);
    }

    public static Carrier reconstitute(UUID id, UUID organizationId, String code, String name, String phone,
                                       String email, boolean active, Instant createdAt, Instant updatedAt) {
        return new Carrier(id, organizationId, code, name, phone, email, active, createdAt, updatedAt);
    }

    public void updateDetails(String name, String phone, String email) {
        this.name = requireName(name);
        this.phone = normalize(phone, 30);
        this.email = normalize(email, 254);
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

    private void touch() {
        updatedAt = Instant.now();
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DomainValidationException("code", "El código del transportista es obligatorio.");
        }
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() > CODE_MAX_LENGTH) {
            throw new DomainValidationException("code",
                    "El código no puede superar %d caracteres.".formatted(CODE_MAX_LENGTH));
        }
        return normalized;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name", "El nombre del transportista es obligatorio.");
        }
        String normalized = name.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "El nombre no puede superar %d caracteres.".formatted(NAME_MAX_LENGTH));
        }
        return normalized;
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainValidationException("field", "No puede superar %d caracteres.".formatted(maxLength));
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

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
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
        return other instanceof Carrier carrier && id.equals(carrier.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Carrier[id=%s, code=%s, name=%s]".formatted(id, code, name);
    }
}
