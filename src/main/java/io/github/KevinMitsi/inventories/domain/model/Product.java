package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Producto del catálogo, global a la organización.
 *
 * <p>No almacena stock: las existencias pertenecen a la pareja (sucursal, producto) y viven
 * en {@code Inventory} (RN-02).
 *
 * <p>Se cuenta siempre en una única unidad de medida, la suya, y no hay factores de
 * conversión: el stock de «Agua Brisa Botella 1 L» son unidades de esa botella, no un número
 * que haya que traducir desde otra presentación. Una presentación distinta —«bolsa x 24»— es
 * un producto distinto, con su propio SKU, su propio stock y su propio precio, enlazado al
 * principal por {@code parentProductId}. Esa relación es solo de agrupación de catálogo: ni
 * el inventario ni las ventas la consultan.
 */
public final class Product {

    private static final int SKU_MAX_LENGTH = 60;
    private static final int BARCODE_MAX_LENGTH = 100;
    private static final int NAME_MAX_LENGTH = 180;

    private final UUID id;
    private final UUID organizationId;
    private final String sku;
    private final UUID parentProductId;
    private final UnitOfMeasure unit;
    private final Instant createdAt;

    private UUID categoryId;
    private String barcode;
    private String name;
    private String description;
    private boolean active;
    private Instant updatedAt;

    private Product(UUID id,
                    UUID organizationId,
                    UUID parentProductId,
                    UUID categoryId,
                    String sku,
                    String barcode,
                    String name,
                    String description,
                    UnitOfMeasure unit,
                    boolean active,
                    Instant createdAt,
                    Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador del producto no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId,
                "El producto debe pertenecer a una organización.");
        this.sku = requireSku(sku);
        this.parentProductId = requireDistinctParent(id, parentProductId);
        this.unit = Objects.requireNonNull(unit, "El producto debe tener una unidad de medida.");
        this.categoryId = categoryId;
        this.barcode = normalizeOptional(barcode, BARCODE_MAX_LENGTH, "barcode");
        this.name = requireName(name);
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    /** Crea un producto principal: el que encabeza su familia de variantes. */
    public static Product create(UUID organizationId,
                                 UUID categoryId,
                                 String sku,
                                 String barcode,
                                 String name,
                                 String description,
                                 UnitOfMeasure unit) {
        Instant now = Instant.now();
        return new Product(UUID.randomUUID(), organizationId, null, categoryId, sku, barcode,
                name, description, unit, true, now, now);
    }

    /**
     * Crea una variante de este producto.
     *
     * <p>La variante es un producto completo, no una presentación: tiene SKU propio, se
     * inventaría por separado y se cuenta en su propia unidad. Solo hereda la organización y,
     * si no se indica otra, la categoría del principal.
     */
    public Product createVariant(String variantSku,
                                 String variantBarcode,
                                 String variantName,
                                 String variantDescription,
                                 UUID variantCategoryId,
                                 UnitOfMeasure variantUnit) {
        if (isVariant()) {
            throw new DomainValidationException("parentProductId",
                    "Una variante no puede tener variantes propias: el catálogo es de un solo nivel.");
        }

        Instant now = Instant.now();
        return new Product(UUID.randomUUID(), organizationId, id,
                variantCategoryId == null ? categoryId : variantCategoryId,
                variantSku, variantBarcode, variantName, variantDescription,
                variantUnit == null ? unit : variantUnit, true, now, now);
    }

    public static Product reconstitute(UUID id,
                                       UUID organizationId,
                                       UUID parentProductId,
                                       UUID categoryId,
                                       String sku,
                                       String barcode,
                                       String name,
                                       String description,
                                       UnitOfMeasure unit,
                                       boolean active,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Product(id, organizationId, parentProductId, categoryId, sku, barcode, name,
                description, unit, active, createdAt, updatedAt);
    }

    public void updateDetails(UUID categoryId, String barcode, String name, String description) {
        this.categoryId = categoryId;
        this.barcode = normalizeOptional(barcode, BARCODE_MAX_LENGTH, "barcode");
        this.name = requireName(name);
        this.description = description == null || description.isBlank() ? null : description.trim();
        touch();
    }

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

    public boolean isVariant() {
        return parentProductId != null;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static UUID requireDistinctParent(UUID id, UUID parentProductId) {
        if (id.equals(parentProductId)) {
            throw new DomainValidationException("parentProductId",
                    "Un producto no puede ser variante de sí mismo.");
        }
        return parentProductId;
    }

    private static String requireSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new DomainValidationException("sku", "El SKU del producto es obligatorio.");
        }
        String normalized = sku.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > SKU_MAX_LENGTH) {
            throw new DomainValidationException("sku",
                    "El SKU no puede superar %d caracteres.".formatted(SKU_MAX_LENGTH));
        }
        return normalized;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name", "El nombre del producto es obligatorio.");
        }
        String normalized = name.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "El nombre no puede superar %d caracteres.".formatted(NAME_MAX_LENGTH));
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainValidationException(field,
                    "No puede superar %d caracteres.".formatted(maxLength));
        }
        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getParentProductId() {
        return parentProductId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getSku() {
        return sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UnitOfMeasure getUnit() {
        return unit;
    }

    public UUID getUnitId() {
        return unit.id();
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
        return other instanceof Product product && id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Product[id=%s, sku=%s, name=%s, unit=%s, variant=%s, active=%s]"
                .formatted(id, sku, name, unit.code(), isVariant(), active);
    }
}
