package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product (agregado de catálogo)")
class ProductTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private UnitOfMeasure bottle;
    private UnitOfMeasure bag;

    @BeforeEach
    void setUp() {
        bottle = UnitOfMeasure.create("UNIT", "Unidad", "und");
        bag = UnitOfMeasure.create("PACK", "Paquete", "paq");
    }

    private Product newProduct() {
        return Product.create(ORGANIZATION_ID, CATEGORY_ID, "BEB-BRISA-BOT-1L", "7701234567890",
                "Agua Brisa Botella 1 L", "Botella PET", bottle);
    }

    @Nested
    @DisplayName("Creación")
    class Creation {

        @Test
        @DisplayName("un producto nuevo nace activo, principal y con su unidad")
        void createsActivePrincipalProduct() {
            // Arrange & Act
            Product product = newProduct();

            // Assert
            assertThat(product.getId()).isNotNull();
            assertThat(product.isActive()).isTrue();
            assertThat(product.isVariant()).isFalse();
            assertThat(product.getParentProductId()).isNull();
            assertThat(product.getUnit()).isEqualTo(bottle);
        }

        @Test
        @DisplayName("el SKU se normaliza a mayúsculas y se recortan los espacios")
        void normalizesSku() {
            // Arrange & Act
            Product product = Product.create(ORGANIZATION_ID, null, "  beb-agua-600 ",
                    null, "Agua", null, bottle);

            // Assert
            assertThat(product.getSku()).isEqualTo("BEB-AGUA-600");
        }

        @Test
        @DisplayName("rechaza crear un producto sin unidad de medida")
        void rejectsMissingUnit() {
            // Arrange, Act & Assert
            assertThatThrownBy(() -> Product.create(ORGANIZATION_ID, null, "SKU-1",
                    null, "Producto", null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("unidad de medida");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("rechaza un SKU en blanco")
        void rejectsBlankSku(String sku) {
            // Arrange, Act & Assert
            assertThatThrownBy(() -> Product.create(ORGANIZATION_ID, null, sku,
                    null, "Producto", null, bottle))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("SKU");
        }

        @Test
        @DisplayName("un código de barras en blanco se guarda como nulo")
        void blankBarcodeBecomesNull() {
            // Arrange & Act
            Product product = Product.create(ORGANIZATION_ID, null, "SKU-1", "   ",
                    "Producto", null, bottle);

            // Assert
            assertThat(product.getBarcode()).isNull();
        }
    }

    @Nested
    @DisplayName("Variantes")
    class Variants {

        @Test
        @DisplayName("una variante es un producto propio, con su SKU y su enlace al principal")
        void variantIsAnAutonomousProduct() {
            // Arrange
            Product principal = newProduct();

            // Act
            Product variant = principal.createVariant("BEB-BRISA-BOL-24", "7709999999999",
                    "Agua Brisa Bolsa x 24", "Bolsa", null, bag);

            // Assert
            assertThat(variant.getId()).isNotEqualTo(principal.getId());
            assertThat(variant.getSku()).isEqualTo("BEB-BRISA-BOL-24");
            assertThat(variant.getParentProductId()).isEqualTo(principal.getId());
            assertThat(variant.isVariant()).isTrue();
            assertThat(variant.getUnit()).isEqualTo(bag);
            assertThat(variant.isActive()).isTrue();
        }

        @Test
        @DisplayName("la variante hereda organización, y categoría y unidad si no se indican")
        void variantInheritsDefaults() {
            // Arrange
            Product principal = newProduct();

            // Act
            Product variant = principal.createVariant("BEB-BRISA-BOT-1L-GAS", null,
                    "Agua Brisa Botella 1 L con gas", null, null, null);

            // Assert
            assertThat(variant.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(variant.getCategoryId()).isEqualTo(CATEGORY_ID);
            assertThat(variant.getUnit()).isEqualTo(bottle);
        }

        @Test
        @DisplayName("la categoría y la unidad propias ganan a las heredadas")
        void variantOverridesDefaults() {
            // Arrange
            Product principal = newProduct();
            UUID otherCategoryId = UUID.randomUUID();

            // Act
            Product variant = principal.createVariant("BEB-BRISA-BOL-24", null,
                    "Agua Brisa Bolsa x 24", null, otherCategoryId, bag);

            // Assert
            assertThat(variant.getCategoryId()).isEqualTo(otherCategoryId);
            assertThat(variant.getUnit()).isEqualTo(bag);
        }

        @Test
        @DisplayName("una variante no puede tener variantes propias")
        void rejectsNestedVariants() {
            // Arrange
            Product variant = newProduct().createVariant("BEB-BRISA-BOL-24", null,
                    "Agua Brisa Bolsa x 24", null, null, bag);

            // Act & Assert
            assertThatThrownBy(() -> variant.createVariant("OTRO", null, "Otro", null, null, bag))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("un solo nivel");
        }

        @Test
        @DisplayName("un producto no puede ser variante de sí mismo")
        void rejectsSelfParent() {
            // Arrange
            UUID id = UUID.randomUUID();

            // Act & Assert
            assertThatThrownBy(() -> Product.reconstitute(id, ORGANIZATION_ID, id, null,
                    "SKU-1", null, "Producto", null, bottle, true,
                    java.time.Instant.now(), java.time.Instant.now()))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("variante de sí mismo");
        }
    }

    @Nested
    @DisplayName("Ciclo de vida")
    class Lifecycle {

        @Test
        @DisplayName("dar de baja y reactivar son idempotentes")
        void statusChangesAreIdempotent() {
            // Arrange
            Product product = newProduct();

            // Act
            product.deactivate();
            product.deactivate();

            // Assert
            assertThat(product.isActive()).isFalse();

            // Act
            product.activate();
            product.activate();

            // Assert
            assertThat(product.isActive()).isTrue();
        }

        @Test
        @DisplayName("actualizar los datos no altera el SKU, la organización ni la unidad")
        void updateKeepsIdentity() {
            // Arrange
            Product product = newProduct();
            String originalSku = product.getSku();
            UUID newCategoryId = UUID.randomUUID();

            // Act
            product.updateDetails(newCategoryId, "7709999999999", "Agua Brisa Botella 1.5 L", "Nueva");

            // Assert
            assertThat(product.getName()).isEqualTo("Agua Brisa Botella 1.5 L");
            assertThat(product.getCategoryId()).isEqualTo(newCategoryId);
            assertThat(product.getSku()).isEqualTo(originalSku);
            assertThat(product.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(product.getUnit()).isEqualTo(bottle);
        }
    }
}
