package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Lista de precios (EP-06, RF-29, HU-25).
 *
 * <p>Soporta distintas políticas comerciales sobre el mismo catálogo: minorista, mayorista,
 * promocional. Los precios concretos por producto viven en {@link ProductPrice}, una entidad
 * aparte y no una colección anidada aquí — a diferencia de las presentaciones de
 * {@link Product}, una lista de precios puede cubrir todo el catálogo, y cargarla completa
 * cada vez que se consulta la cabecera sería desproporcionado.
 */
public final class PriceList {

    private static final int CODE_MAX_LENGTH = 30;
    private static final int NAME_MAX_LENGTH = 100;

    private final UUID id;
    private final UUID organizationId;
    private final String code;
    private final Instant createdAt;

    private String name;
    private String description;
    private boolean active;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private Instant updatedAt;

    private PriceList(UUID id, UUID organizationId, String code, String name, String description, boolean active,
                      LocalDate validFrom, LocalDate validUntil, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la lista no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId, "La lista debe pertenecer a una organización.");
        this.code = requireCode(code);
        this.name = requireName(name);
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.active = active;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        requireValidRange(validFrom, validUntil);
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    public static PriceList create(UUID organizationId, String code, String name, String description,
                                   LocalDate validFrom, LocalDate validUntil) {
        Instant now = Instant.now();
        return new PriceList(UUID.randomUUID(), organizationId, code, name, description, true,
                validFrom, validUntil, now, now);
    }

    public static PriceList reconstitute(UUID id, UUID organizationId, String code, String name, String description,
                                         boolean active, LocalDate validFrom, LocalDate validUntil,
                                         Instant createdAt, Instant updatedAt) {
        return new PriceList(id, organizationId, code, name, description, active, validFrom, validUntil,
                createdAt, updatedAt);
    }

    public void updateDetails(String name, String description, LocalDate validFrom, LocalDate validUntil) {
        requireValidRange(validFrom, validUntil);
        this.name = requireName(name);
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.validFrom = validFrom;
        this.validUntil = validUntil;
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

    private static void requireValidRange(LocalDate validFrom, LocalDate validUntil) {
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new DomainValidationException("validUntil",
                    "La fecha de fin de vigencia no puede ser anterior a la de inicio.");
        }
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DomainValidationException("code", "El código de la lista de precios es obligatorio.");
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
            throw new DomainValidationException("name", "El nombre de la lista de precios es obligatorio.");
        }
        String normalized = name.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "El nombre no puede superar %d caracteres.".formatted(NAME_MAX_LENGTH));
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

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
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
        return other instanceof PriceList priceList && id.equals(priceList.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
