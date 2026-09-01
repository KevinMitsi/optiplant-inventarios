package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Empresa propietaria de las sucursales.
 *
 * <p>El sistema opera hoy sobre una sola organización (supuesto S-01), pero modelarla como
 * entidad en lugar de darla por implícita evita que esa suposición quede grabada en el
 * esquema. Todo lo que es global —productos, categorías, proveedores, listas de precios—
 * cuelga de ella, así que es también la frontera natural de aislamiento: ninguna consulta
 * debe poder cruzar de una organización a otra.
 */
public final class Organization {

    private static final int CODE_MAX_LENGTH = 30;
    private static final int NAME_MAX_LENGTH = 150;
    private static final int LEGAL_NAME_MAX_LENGTH = 200;
    private static final int TAX_ID_MAX_LENGTH = 50;

    private final UUID id;
    private final String code;
    private final Instant createdAt;

    private String name;
    private String legalName;
    private String taxId;
    private boolean active;
    private Instant updatedAt;

    private Organization(UUID id,
                         String code,
                         String name,
                         String legalName,
                         String taxId,
                         boolean active,
                         Instant createdAt,
                         Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la organización no puede ser nulo.");
        this.code = requireCode(code);
        this.name = requireName(name);
        this.legalName = optional(legalName, LEGAL_NAME_MAX_LENGTH, "razón social");
        this.taxId = optional(taxId, TAX_ID_MAX_LENGTH, "identificación tributaria");
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    public static Organization create(String code, String name, String legalName, String taxId) {
        Instant now = Instant.now();
        return new Organization(UUID.randomUUID(), code, name, legalName, taxId, true, now, now);
    }

    /** Reconstruye una organización ya persistida. Solo lo usa el adaptador de persistencia. */
    public static Organization reconstitute(UUID id,
                                            String code,
                                            String name,
                                            String legalName,
                                            String taxId,
                                            boolean active,
                                            Instant createdAt,
                                            Instant updatedAt) {
        return new Organization(id, code, name, legalName, taxId, active, createdAt, updatedAt);
    }

    public void updateDetails(String name, String legalName, String taxId) {
        this.name = requireName(name);
        this.legalName = optional(legalName, LEGAL_NAME_MAX_LENGTH, "razón social");
        this.taxId = optional(taxId, TAX_ID_MAX_LENGTH, "identificación tributaria");
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        if (!active) {
            return;
        }
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (active) {
            return;
        }
        this.active = true;
        this.updatedAt = Instant.now();
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DomainValidationException("code", "El código de la organización es obligatorio.");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > CODE_MAX_LENGTH) {
            throw new DomainValidationException("code",
                    "El código de la organización no puede superar %d caracteres.".formatted(CODE_MAX_LENGTH));
        }
        return normalized;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name", "El nombre de la organización es obligatorio.");
        }
        String normalized = name.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "El nombre de la organización no puede superar %d caracteres.".formatted(NAME_MAX_LENGTH));
        }
        return normalized;
    }

    private static String optional(String value, int maxLength, String fieldLabel) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainValidationException(fieldLabel,
                    "El campo %s no puede superar %d caracteres.".formatted(fieldLabel, maxLength));
        }
        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTaxId() {
        return taxId;
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
        return other instanceof Organization organization && id.equals(organization.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Organization[id=%s, code=%s, name=%s]".formatted(id, code, name);
    }
}
