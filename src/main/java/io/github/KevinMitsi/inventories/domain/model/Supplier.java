package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Proveedor al que se le compran productos (EP-05).
 *
 * <p>El módulo obligatorio de compras exige órdenes <em>a</em> un proveedor y su histórico
 * (RF-17, RF-22), así que la entidad es imprescindible aunque su CRUD avanzado sea de
 * segundo nivel (PHASE1.md §33). Sigue el mismo patrón de baja lógica que el resto de
 * maestros del catálogo: aparece en órdenes de compra históricas y eliminarlo dejaría ese
 * histórico sin poder explicarse.
 */
public final class Supplier {

    private static final int CODE_MAX_LENGTH = 30;
    private static final int NAME_MAX_LENGTH = 180;

    private final UUID id;
    private final UUID organizationId;
    private final String code;
    private final Instant createdAt;

    private String name;
    private String taxId;
    private String email;
    private String phone;
    private boolean active;
    private Instant updatedAt;

    private Supplier(UUID id, UUID organizationId, String code, String name, String taxId, String email,
                     String phone, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador del proveedor no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId, "El proveedor debe pertenecer a una organización.");
        this.code = requireCode(code);
        this.name = requireName(name);
        this.taxId = normalize(taxId, 50);
        this.email = normalize(email, 254);
        this.phone = normalize(phone, 30);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    public static Supplier create(UUID organizationId, String code, String name, String taxId,
                                  String email, String phone) {
        Instant now = Instant.now();
        return new Supplier(UUID.randomUUID(), organizationId, code, name, taxId, email, phone, true, now, now);
    }

    public static Supplier reconstitute(UUID id, UUID organizationId, String code, String name, String taxId,
                                        String email, String phone, boolean active,
                                        Instant createdAt, Instant updatedAt) {
        return new Supplier(id, organizationId, code, name, taxId, email, phone, active, createdAt, updatedAt);
    }

    public void updateDetails(String name, String taxId, String email, String phone) {
        this.name = requireName(name);
        this.taxId = normalize(taxId, 50);
        this.email = normalize(email, 254);
        this.phone = normalize(phone, 30);
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
            throw new DomainValidationException("code", "El código del proveedor es obligatorio.");
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
            throw new DomainValidationException("name", "El nombre del proveedor es obligatorio.");
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

    public String getTaxId() {
        return taxId;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
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
        return other instanceof Supplier supplier && id.equals(supplier.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Supplier[id=%s, code=%s, name=%s]".formatted(id, code, name);
    }
}
