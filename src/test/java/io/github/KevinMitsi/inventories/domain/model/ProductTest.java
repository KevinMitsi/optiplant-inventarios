package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product (agregado de catálogo)")
class ProductTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private UnitOfMeasure bottle;
    private UnitOfMeasure box;
    private UnitOfMeasure pallet;

    @BeforeEach
    void setUp() {
        bottle = UnitOfMeasure.create("UNIT", "Unidad", "und");
        box = UnitOfMeasure.create("BOX", "Caja", "caja");
        pallet = UnitOfMeasure.create("PALLET", "Pallet", "plt");
    }

    private Product newProduct() {
        return Product.create(ORGANIZATION_ID, CATEGORY_ID, "BEB-AGUA-600", "7701234567890",
                "Agua mineral 600 ml", "Botella PET", bottle);
    }

    @Nested
    @DisplayName("Creación")
    class Creation {

        @Test
        @DisplayName("un producto nuevo nace activo y con su unidad base")
        void createsActiveProductWithBaseUnit() {
            // Arrange & Act
            Product product = newProduct();

            // Assert
            assertThat(product.getId()).isNotNull();
            assertThat(product.isActive()).isTrue();
            assertThat(product.getUnits()).hasSize(1);
            assertThat(product.requireBaseUnit().getUnit()).isEqualTo(bottle);
        }

        @Test
        @DisplayName("la unidad base tiene siempre factor de conversión 1")
        void baseUnitFactorIsOne() {
            // Arrange & Act
            Product product = newProduct();

            // Assert
            assertThat(product.requireBaseUnit().getConversionFactor())
                    .isEqualByComparingTo(BigDecimal.ONE);
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
        @DisplayName("rechaza crear un producto sin unidad base")
        void rejectsMissingBaseUnit() {
            // Arrange, Act & Assert
            assertThatThrownBy(() -> Product.create(ORGANIZATION_ID, null, "SKU-1",
                    null, "Producto", null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("unidad base");
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
    @DisplayName("Presentaciones")
    class Units {

        @Test
        @DisplayName("añade una presentación con su factor de conversión")
        void addsUnit() {
            // Arrange
            Product product = newProduct();

            // Act
            product.addUnit(box, new BigDecimal("24"));

            // Assert
            assertThat(product.getUnits()).hasSize(2);
            assertThat(product.hasUnit(box.id())).isTrue();
        }

        @Test
        @DisplayName("rechaza dos presentaciones en la misma unidad de medida")
        void rejectsDuplicateUnit() {
            // Arrange
            Product product = newProduct();
            product.addUnit(box, new BigDecimal("24"));

            // Act & Assert
            assertThatThrownBy(() -> product.addUnit(box, new BigDecimal("12")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ya tiene una presentación");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "-0.5"})
        @DisplayName("rechaza un factor de conversión que no sea positivo")
        void rejectsNonPositiveFactor(String factor) {
            // Arrange
            Product product = newProduct();

            // Act & Assert
            assertThatThrownBy(() -> product.addUnit(box, new BigDecimal(factor)))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("mayor que cero");
        }

        @Test
        @DisplayName("convierte una cantidad de la presentación a unidades base")
        void convertsToBaseQuantity() {
            // Arrange
            Product product = newProduct();
            product.addUnit(box, new BigDecimal("24"));
            UUID boxUnitId = product.getUnits().stream()
                    .filter(unit -> unit.getUnitId().equals(box.id()))
                    .findFirst().orElseThrow().getId();

            // Act
            Quantity baseQuantity = product.toBaseQuantity(boxUnitId, Quantity.of(3));

            // Assert
            assertThat(baseQuantity).isEqualTo(Quantity.of(72));
        }

        @Test
        @DisplayName("rechaza cambiar el factor de la unidad base")
        void rejectsChangingBaseUnitFactor() {
            // Arrange
            Product product = newProduct();
            UUID baseUnitId = product.requireBaseUnit().getId();

            // Act & Assert
            assertThatThrownBy(() -> product.changeUnitFactor(baseUnitId, new BigDecimal("5")))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("unidad base siempre tiene factor 1");
        }

        @Test
        @DisplayName("rechaza operar sobre una presentación que no pertenece al producto")
        void rejectsForeignProductUnit() {
            // Arrange
            Product product = newProduct();
            UUID foreignUnitId = UUID.randomUUID();

            // Act & Assert
            assertThatThrownBy(() -> product.changeUnitFactor(foreignUnitId, new BigDecimal("5")))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("no pertenece a este producto");
        }
    }

    @Nested
    @DisplayName("Invariante de la unidad base")
    class BaseUnitInvariant {

        @Test
        @DisplayName("siempre existe exactamente una unidad base")
        void alwaysExactlyOneBaseUnit() {
            // Arrange
            Product product = newProduct();

            // Act
            product.addUnit(box, new BigDecimal("24"));
            product.addUnit(pallet, new BigDecimal("1200"));

            // Assert
            assertThat(product.getUnits().stream().filter(ProductUnit::isBaseUnit))
                    .as("sin una base única no habría forma de saber en qué se mide el stock")
                    .hasSize(1);
        }

        @Test
        @DisplayName("cambiar la base la traslada y degrada la anterior con su nuevo factor")
        void changingBaseUnitSwapsRoles() {
            // Arrange
            Product product = newProduct();
            product.addUnit(box, new BigDecimal("24"));
            UUID previousBaseId = product.requireBaseUnit().getId();
            UUID boxUnitId = product.getUnits().stream()
                    .filter(unit -> unit.getUnitId().equals(box.id()))
                    .findFirst().orElseThrow().getId();

            // Act
            product.changeBaseUnit(boxUnitId, new BigDecimal("0.041667"));

            // Assert
            assertThat(product.requireBaseUnit().getId()).isEqualTo(boxUnitId);
            assertThat(product.requireBaseUnit().getConversionFactor())
                    .isEqualByComparingTo(BigDecimal.ONE);
            assertThat(product.findUnitById(previousBaseId).orElseThrow().getConversionFactor())
                    .isEqualByComparingTo(new BigDecimal("0.041667"));
            assertThat(product.getUnits().stream().filter(ProductUnit::isBaseUnit)).hasSize(1);
        }

        @Test
        @DisplayName("designar como base la que ya lo es no cambia nada")
        void changingToSameBaseIsNoOp() {
            // Arrange
            Product product = newProduct();
            UUID baseUnitId = product.requireBaseUnit().getId();

            // Act
            product.changeBaseUnit(baseUnitId, new BigDecimal("5"));

            // Assert
            assertThat(product.requireBaseUnit().getId()).isEqualTo(baseUnitId);
            assertThat(product.requireBaseUnit().getConversionFactor())
                    .isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("rechaza designar como base una presentación dada de baja")
        void rejectsInactiveUnitAsBase() {
            // Arrange
            Product product = newProduct();
            product.addUnit(box, new BigDecimal("24"));
            UUID boxUnitId = product.getUnits().stream()
                    .filter(unit -> unit.getUnitId().equals(box.id()))
                    .findFirst().orElseThrow().getId();
            product.deactivateUnit(boxUnitId);

            // Act & Assert
            assertThatThrownBy(() -> product.changeBaseUnit(boxUnitId, BigDecimal.ONE))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("dada de baja");
        }

        @Test
        @DisplayName("rechaza dar de baja la unidad base")
        void rejectsDeactivatingBaseUnit() {
            // Arrange
            Product product = newProduct();
            UUID baseUnitId = product.requireBaseUnit().getId();

            // Act & Assert
            assertThatThrownBy(() -> product.deactivateUnit(baseUnitId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("No se puede dar de baja la unidad base");
        }

        @Test
        @DisplayName("tras cambiar la base, la anterior ya se puede retirar")
        void previousBaseCanBeDeactivatedAfterSwap() {
            // Arrange
            Product product = newProduct();
            product.addUnit(box, new BigDecimal("24"));
            UUID previousBaseId = product.requireBaseUnit().getId();
            UUID boxUnitId = product.getUnits().stream()
                    .filter(unit -> unit.getUnitId().equals(box.id()))
                    .findFirst().orElseThrow().getId();
            product.changeBaseUnit(boxUnitId, new BigDecimal("0.041667"));

            // Act
            product.deactivateUnit(previousBaseId);

            // Assert
            assertThat(product.findUnitById(previousBaseId).orElseThrow().isActive()).isFalse();
            assertThat(product.getActiveUnits()).hasSize(1);
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
        @DisplayName("actualizar los datos no altera el SKU ni la organización")
        void updateKeepsIdentity() {
            // Arrange
            Product product = newProduct();
            String originalSku = product.getSku();
            UUID newCategoryId = UUID.randomUUID();

            // Act
            product.updateDetails(newCategoryId, "7709999999999", "Agua mineral 1 L", "Nueva");

            // Assert
            assertThat(product.getName()).isEqualTo("Agua mineral 1 L");
            assertThat(product.getCategoryId()).isEqualTo(newCategoryId);
            assertThat(product.getSku()).isEqualTo(originalSku);
            assertThat(product.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
        }

        @Test
        @DisplayName("la lista de presentaciones que se expone es inmutable")
        void exposedUnitsAreUnmodifiable() {
            // Arrange
            Product product = newProduct();

            // Act & Assert
            assertThatThrownBy(() -> product.getUnits().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
