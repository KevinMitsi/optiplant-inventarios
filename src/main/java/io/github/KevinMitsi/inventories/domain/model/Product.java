package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Producto del catálogo, global a la organización.
 *
 * <p>No almacena stock: las existencias pertenecen a la pareja (sucursal, producto) y viven
 * en {@code Inventory} (RN-02).
 *
 * <p>Es el agregado raíz de sus presentaciones. Que las contenga en lugar de tratarlas como
 * entidades sueltas es lo que permite garantizar el invariante que ninguna restricción de
 * columna puede expresar: <b>siempre existe exactamente una unidad base activa</b>. Sin ella
 * no habría forma de convertir cantidades entre presentaciones ni de saber en qué se mide el
 * stock.
 */
public final class Product {

    private static final int SKU_MAX_LENGTH = 60;
    private static final int BARCODE_MAX_LENGTH = 100;
    private static final int NAME_MAX_LENGTH = 180;

    private final UUID id;
    private final UUID organizationId;
    private final String sku;
    private final Instant createdAt;

    private UUID categoryId;
    private String barcode;
    private String name;
    private String description;
    private boolean active;
    private Instant updatedAt;

    private final List<ProductUnit> units;

    private Product(UUID id,
                    UUID organizationId,
                    UUID categoryId,
                    String sku,
                    String barcode,
                    String name,
                    String description,
                    boolean active,
                    List<ProductUnit> units,
                    Instant createdAt,
                    Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador del producto no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId,
                "El producto debe pertenecer a una organización.");
        this.sku = requireSku(sku);
        this.categoryId = categoryId;
        this.barcode = normalizeOptional(barcode, BARCODE_MAX_LENGTH, "barcode");
        this.name = requireName(name);
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.active = active;
        this.units = units == null ? new ArrayList<>() : new ArrayList<>(units);
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    /**
     * Crea un producto con su unidad base.
     *
     * <p>La unidad base es obligatoria desde el principio: un producto sin ella no podría
     * recibir stock, porque no habría forma de saber en qué se mide.
     */
    public static Product create(UUID organizationId,
                                 UUID categoryId,
                                 String sku,
                                 String barcode,
                                 String name,
                                 String description,
                                 UnitOfMeasure baseUnit) {
        Objects.requireNonNull(baseUnit, "El producto debe tener una unidad base.");

        Instant now = Instant.now();
        List<ProductUnit> units = new ArrayList<>();
        units.add(ProductUnit.createBase(baseUnit));

        return new Product(UUID.randomUUID(), organizationId, categoryId, sku, barcode,
                name, description, true, units, now, now);
    }

    public static Product reconstitute(UUID id,
                                       UUID organizationId,
                                       UUID categoryId,
                                       String sku,
                                       String barcode,
                                       String name,
                                       String description,
                                       boolean active,
                                       List<ProductUnit> units,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Product(id, organizationId, categoryId, sku, barcode, name,
                description, active, units, createdAt, updatedAt);
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

    public void addUnit(UnitOfMeasure unit, BigDecimal conversionFactor) {
        Objects.requireNonNull(unit, "La presentación debe referenciar una unidad de medida.");

        if (hasUnit(unit.id())) {
            throw new BusinessRuleViolationException("RF-09",
                    "El producto ya tiene una presentación en la unidad '%s'.".formatted(unit.code()));
        }

        units.add(ProductUnit.create(unit, conversionFactor));
        touch();
    }

    public void changeUnitFactor(UUID productUnitId, BigDecimal conversionFactor) {
        requireUnit(productUnitId).changeFactor(conversionFactor);
        touch();
    }

    /**
     * Designa otra presentación como base.
     *
     * <p>La anterior deja de serlo y necesita un factor propio, ya que su equivalencia con la
     * nueva base no es deducible: si la base pasa de botella a caja de 24, el factor de la
     * botella pasa a ser 1/24, un número que el sistema no puede inventar sin conocer la
     * intención del negocio. Por eso se exige explícitamente.
     */
    public void changeBaseUnit(UUID newBaseProductUnitId, BigDecimal previousBaseNewFactor) {
        ProductUnit newBase = requireUnit(newBaseProductUnitId);

        if (newBase.isBaseUnit()) {
            return;
        }
        if (!newBase.isActive()) {
            throw new BusinessRuleViolationException("RF-09",
                    "Una presentación dada de baja no puede ser la unidad base.");
        }

        findBaseUnit().ifPresent(previousBase -> previousBase.demoteFromBase(previousBaseNewFactor));
        newBase.promoteToBase();
        touch();
    }

    /**
     * Da de baja una presentación.
     *
     * <p>La base no se puede desactivar: dejaría al producto sin referencia para medir su
     * stock. Para retirarla, primero hay que designar otra como base.
     */
    public void deactivateUnit(UUID productUnitId) {
        ProductUnit productUnit = requireUnit(productUnitId);

        if (productUnit.isBaseUnit()) {
            throw new BusinessRuleViolationException("RF-09",
                    "No se puede dar de baja la unidad base. Designe antes otra presentación como base.");
        }

        productUnit.deactivate();
        touch();
    }

    public void activateUnit(UUID productUnitId) {
        requireUnit(productUnitId).activate();
        touch();
    }

    public Optional<ProductUnit> findBaseUnit() {
        return units.stream().filter(ProductUnit::isBaseUnit).findFirst();
    }

    public ProductUnit requireBaseUnit() {
        return findBaseUnit().orElseThrow(() -> new BusinessRuleViolationException("RF-09",
                "El producto '%s' no tiene unidad base definida.".formatted(sku)));
    }

    public Optional<ProductUnit> findUnitById(UUID productUnitId) {
        return units.stream().filter(unit -> unit.getId().equals(productUnitId)).findFirst();
    }

    public boolean hasUnit(UUID unitOfMeasureId) {
        return units.stream().anyMatch(unit -> unit.getUnitId().equals(unitOfMeasureId));
    }

    /** Convierte una cantidad expresada en una presentación concreta a unidades base. */
    public Quantity toBaseQuantity(UUID productUnitId, Quantity quantity) {
        return requireUnit(productUnitId).toBaseQuantity(quantity);
    }

    public List<ProductUnit> getUnits() {
        return Collections.unmodifiableList(units);
    }

    public List<ProductUnit> getActiveUnits() {
        return units.stream().filter(ProductUnit::isActive).toList();
    }

    private ProductUnit requireUnit(UUID productUnitId) {
        return findUnitById(productUnitId)
                .orElseThrow(() -> new DomainValidationException("productUnitId",
                        "La presentación indicada no pertenece a este producto."));
    }

    private void touch() {
        this.updatedAt = Instant.now();
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
        return "Product[id=%s, sku=%s, name=%s, active=%s]".formatted(id, sku, name, active);
    }
}
