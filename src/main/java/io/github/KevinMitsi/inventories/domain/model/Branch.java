package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Sucursal de la organización.
 *
 * <p>Es la unidad de autonomía operativa: el stock, las ventas, las compras y los extremos
 * de una transferencia pertenecen siempre a una sucursal concreta (RN-02). También delimita
 * la autorización (RN-12, RN-13).
 *
 * <p>El código es inmutable una vez creada: aparece en números de documento y referencias
 * operativas, así que cambiarlo rompería la trazabilidad de lo ya registrado.
 */
public final class Branch {

    private static final int CODE_MAX_LENGTH = 30;
    private static final int NAME_MAX_LENGTH = 150;
    private static final int ADDRESS_MAX_LENGTH = 250;
    private static final int CITY_MAX_LENGTH = 100;
    private static final int PHONE_MAX_LENGTH = 30;

    private final UUID id;
    private final UUID organizationId;
    private final String code;
    private final Instant createdAt;

    private String name;
    private String addressLine;
    private String city;
    private String countryCode;
    private String phone;
    private boolean active;
    private Instant updatedAt;

    private Branch(UUID id,
                   UUID organizationId,
                   String code,
                   String name,
                   String addressLine,
                   String city,
                   String countryCode,
                   String phone,
                   boolean active,
                   Instant createdAt,
                   Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la sucursal no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId,
                "La sucursal debe pertenecer a una organización.");
        this.code = requireCode(code);
        this.name = requireName(name);
        this.addressLine = normalizeOptional(addressLine, ADDRESS_MAX_LENGTH, "dirección");
        this.city = normalizeOptional(city, CITY_MAX_LENGTH, "ciudad");
        this.countryCode = requireCountryCode(countryCode);
        this.phone = normalizeOptional(phone, PHONE_MAX_LENGTH, "teléfono");
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    public static Branch create(UUID organizationId,
                                String code,
                                String name,
                                String addressLine,
                                String city,
                                String countryCode,
                                String phone) {
        Instant now = Instant.now();
        return new Branch(UUID.randomUUID(), organizationId, code, name,
                addressLine, city, countryCode, phone, true, now, now);
    }

    /**
     * Reconstruye una sucursal ya persistida. Sigue validando los invariantes, de modo que un
     * dato corrupto en la base se detecta al cargarlo.
     */
    public static Branch reconstitute(UUID id,
                                      UUID organizationId,
                                      String code,
                                      String name,
                                      String addressLine,
                                      String city,
                                      String countryCode,
                                      String phone,
                                      boolean active,
                                      Instant createdAt,
                                      Instant updatedAt) {
        return new Branch(id, organizationId, code, name, addressLine, city,
                countryCode, phone, active, createdAt, updatedAt);
    }

    public void updateDetails(String name,
                              String addressLine,
                              String city,
                              String countryCode,
                              String phone) {
        this.name = requireName(name);
        this.addressLine = normalizeOptional(addressLine, ADDRESS_MAX_LENGTH, "dirección");
        this.city = normalizeOptional(city, CITY_MAX_LENGTH, "ciudad");
        this.countryCode = requireCountryCode(countryCode);
        this.phone = normalizeOptional(phone, PHONE_MAX_LENGTH, "teléfono");
        touch();
    }

    /** Baja lógica: la sucursal aparece en el histórico y eliminarla lo dejaría sin explicar. */
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

    public boolean canOperate() {
        return active;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    /** Se normaliza a mayúsculas para que la unicidad no dependa de cómo se escribiera. */
    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DomainValidationException("code", "El código de la sucursal es obligatorio.");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > CODE_MAX_LENGTH) {
            throw new DomainValidationException("code",
                    "El código de la sucursal no puede superar %d caracteres.".formatted(CODE_MAX_LENGTH));
        }
        return normalized;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name", "El nombre de la sucursal es obligatorio.");
        }
        String normalized = name.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "El nombre de la sucursal no puede superar %d caracteres.".formatted(NAME_MAX_LENGTH));
        }
        return normalized;
    }

    private static String requireCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 2) {
            throw new DomainValidationException("countryCode",
                    "El código de país debe tener exactamente 2 caracteres (ISO 3166-1 alfa-2).");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength, String fieldLabel) {
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

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
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

    /** Igualdad por identidad: dos instancias con el mismo id son la misma sucursal. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Branch branch && id.equals(branch.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Branch[id=%s, code=%s, name=%s, active=%s]".formatted(id, code, name, active);
    }
}
