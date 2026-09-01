package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.ProductUnit;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.CategoryJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductUnitJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UnitOfMeasureJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del mapeador generado por MapStruct.
 *
 * <p>Se usa la implementación generada y no un doble: lo que se comprueba es que la
 * correspondencia entre campos sea correcta, y con un doble no habría nada que verificar.
 */
@DisplayName("CatalogPersistenceMapper")
class CatalogPersistenceMapperTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    private CatalogPersistenceMapper mapper;
    private UnitOfMeasure bottle;
    private UnitOfMeasure box;

    @BeforeEach
    void setUp() {
        mapper = new CatalogPersistenceMapperImpl();
        bottle = UnitOfMeasure.create("UNIT", "Unidad", "und");
        box = UnitOfMeasure.create("BOX", "Caja", "caja");
    }

    @Nested
    @DisplayName("Categoría")
    class Categories {

        @Test
        @DisplayName("conserva todos los campos en el viaje de ida y vuelta")
        void roundTripPreservesFields() {
            // Arrange
            Category original = Category.reconstitute(UUID.randomUUID(), ORGANIZATION_ID, "BEB",
                    "Bebidas", "Frías y calientes", true,
                    Instant.parse("2026-01-15T09:30:00Z"), Instant.parse("2026-08-27T14:05:22Z"));

            // Act
            Category result = mapper.toDomain(mapper.toEntity(original));

            // Assert
            assertThat(result.getId()).isEqualTo(original.getId());
            assertThat(result.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(result.getCode()).isEqualTo("BEB");
            assertThat(result.getName()).isEqualTo("Bebidas");
            assertThat(result.getDescription()).isEqualTo("Frías y calientes");
            assertThat(result.isActive()).isTrue();
            assertThat(result.getCreatedAt()).isEqualTo(original.getCreatedAt());
            assertThat(result.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
        }

        @Test
        @DisplayName("devuelve nulo ante una entidad nula")
        void handlesNullEntity() {
            // Arrange, Act & Assert
            assertThat(mapper.toDomain((CategoryJpaEntity) null)).isNull();
        }
    }

    @Nested
    @DisplayName("Unidad de medida")
    class Units {

        @Test
        @DisplayName("conserva todos los campos en el viaje de ida y vuelta")
        void roundTripPreservesFields() {
            // Act
            UnitOfMeasure result = mapper.toDomain(mapper.toEntity(box));

            // Assert
            assertThat(result).isEqualTo(box);
        }

        @Test
        @DisplayName("devuelve nulo ante una entidad nula")
        void handlesNullEntity() {
            // Arrange, Act & Assert
            assertThat(mapper.toDomain((UnitOfMeasureJpaEntity) null)).isNull();
        }
    }

    @Nested
    @DisplayName("Producto con presentaciones")
    class Products {

        @Test
        @DisplayName("conserva el producto y todas sus presentaciones")
        void roundTripPreservesUnits() {
            // Arrange
            Product original = Product.create(ORGANIZATION_ID, UUID.randomUUID(), "BEB-AGUA-600",
                    "7701234567890", "Agua mineral 600 ml", "Botella PET", bottle);
            original.addUnit(box, new BigDecimal("24"));

            // Act
            Product result = mapper.toDomain(mapper.toEntity(original));

            // Assert
            assertThat(result.getSku()).isEqualTo("BEB-AGUA-600");
            assertThat(result.getBarcode()).isEqualTo("7701234567890");
            assertThat(result.getUnits()).hasSize(2);
            assertThat(result.requireBaseUnit().getUnit().code()).isEqualTo("UNIT");
            assertThat(result.hasUnit(box.id())).isTrue();
        }

        @Test
        @DisplayName("conserva el factor de conversión sin perder decimales")
        void preservesConversionFactorScale() {
            // Arrange
            Product original = Product.create(ORGANIZATION_ID, null, "SKU-1", null,
                    "Producto", null, bottle);
            original.addUnit(box, new BigDecimal("0.041667"));

            // Act
            Product result = mapper.toDomain(mapper.toEntity(original));

            // Assert
            ProductUnit boxUnit = result.getUnits().stream()
                    .filter(unit -> unit.getUnitId().equals(box.id()))
                    .findFirst().orElseThrow();
            assertThat(boxUnit.getConversionFactor()).isEqualByComparingTo("0.041667");
        }

        @Test
        @DisplayName("asocia cada presentación a su producto, que es quien escribe la clave foránea")
        void wiresUnitsToOwningProduct() {
            // Arrange
            Product original = Product.create(ORGANIZATION_ID, null, "SKU-1", null,
                    "Producto", null, bottle);
            original.addUnit(box, new BigDecimal("24"));

            // Act
            ProductJpaEntity entity = mapper.toEntity(original);

            // Assert
            assertThat(entity.getUnits())
                    .isNotEmpty()
                    .allSatisfy(unit -> assertThat(unit.getProduct()).isSameAs(entity));
        }

        @Test
        @DisplayName("devuelve nulo ante entidades nulas")
        void handlesNullEntities() {
            // Arrange, Act & Assert
            assertThat(mapper.toDomain((ProductJpaEntity) null)).isNull();
            assertThat(mapper.toUnitDomain((ProductUnitJpaEntity) null)).isNull();
            assertThat(mapper.toEntity((Product) null)).isNull();
        }
    }
}
