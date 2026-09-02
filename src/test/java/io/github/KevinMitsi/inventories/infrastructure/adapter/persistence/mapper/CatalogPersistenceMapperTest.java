package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.CategoryJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UnitOfMeasureJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    private UnitOfMeasure bag;

    @BeforeEach
    void setUp() {
        mapper = new CatalogPersistenceMapperImpl();
        bottle = UnitOfMeasure.create("UNIT", "Unidad", "und");
        bag = UnitOfMeasure.create("PACK", "Paquete", "paq");
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
            UnitOfMeasure result = mapper.toDomain(mapper.toEntity(bag));

            // Assert
            assertThat(result).isEqualTo(bag);
        }

        @Test
        @DisplayName("devuelve nulo ante una entidad nula")
        void handlesNullEntity() {
            // Arrange, Act & Assert
            assertThat(mapper.toDomain((UnitOfMeasureJpaEntity) null)).isNull();
        }
    }

    @Nested
    @DisplayName("Producto")
    class Products {

        @Test
        @DisplayName("conserva el producto y su unidad en el viaje de ida y vuelta")
        void roundTripPreservesFields() {
            // Arrange
            Product original = Product.create(ORGANIZATION_ID, UUID.randomUUID(), "BEB-BRISA-BOT-1L",
                    "7701234567890", "Agua Brisa Botella 1 L", "Botella PET", bottle);

            // Act
            Product result = mapper.toDomain(mapper.toEntity(original));

            // Assert
            assertThat(result.getSku()).isEqualTo("BEB-BRISA-BOT-1L");
            assertThat(result.getBarcode()).isEqualTo("7701234567890");
            assertThat(result.getUnit()).isEqualTo(bottle);
            assertThat(result.getParentProductId()).isNull();
        }

        @Test
        @DisplayName("conserva el enlace de una variante con su producto principal")
        void roundTripPreservesParentLink() {
            // Arrange
            Product principal = Product.create(ORGANIZATION_ID, null, "SKU-1", null,
                    "Producto", null, bottle);
            Product variant = principal.createVariant("SKU-1-BOL", null, "Producto bolsa",
                    null, null, bag);

            // Act
            Product result = mapper.toDomain(mapper.toEntity(variant));

            // Assert
            assertThat(result.getParentProductId()).isEqualTo(principal.getId());
            assertThat(result.isVariant()).isTrue();
            assertThat(result.getUnit()).isEqualTo(bag);
        }

        @Test
        @DisplayName("devuelve nulo ante entidades nulas")
        void handlesNullEntities() {
            // Arrange, Act & Assert
            assertThat(mapper.toDomain((ProductJpaEntity) null)).isNull();
            assertThat(mapper.toEntity((Product) null)).isNull();
        }
    }
}
