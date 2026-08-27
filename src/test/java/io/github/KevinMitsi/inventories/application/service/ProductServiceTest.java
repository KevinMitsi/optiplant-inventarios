package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.AddProductUnitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateProductCommand;
import io.github.KevinMitsi.inventories.application.port.out.CategoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UnitOfMeasureRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductService")
class ProductServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String SKU = "BEB-AGUA-600";

    @Mock
    private ProductRepositoryPort productRepository;
    @Mock
    private CategoryRepositoryPort categoryRepository;
    @Mock
    private UnitOfMeasureRepositoryPort unitRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;

    @InjectMocks
    private ProductService service;

    private UnitOfMeasure bottle;
    private UnitOfMeasure box;
    private Category category;

    @BeforeEach
    void setUp() {
        bottle = UnitOfMeasure.create("UNIT", "Unidad", "und");
        box = UnitOfMeasure.create("BOX", "Caja", "caja");
        category = Category.reconstitute(CATEGORY_ID, ORGANIZATION_ID, "BEB", "Bebidas",
                null, true, Instant.now(), Instant.now());

        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(unitRepository.findById(bottle.id())).thenReturn(Optional.of(bottle));
        when(unitRepository.findById(box.id())).thenReturn(Optional.of(box));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));
    }

    private CreateProductCommand createCommand() {
        return new CreateProductCommand(ORGANIZATION_ID, CATEGORY_ID, SKU, "7701234567890",
                "Agua mineral 600 ml", "Botella PET", bottle.id());
    }

    private Product existingProduct() {
        return Product.create(ORGANIZATION_ID, CATEGORY_ID, SKU, "7701234567890",
                "Agua mineral 600 ml", null, bottle);
    }

    @Nested
    @DisplayName("Alta")
    class Creation {

        @Test
        @DisplayName("crea el producto con su unidad base")
        void createsProductWithBaseUnit() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);
            when(productRepository.existsByOrganizationIdAndBarcode(eq(ORGANIZATION_ID), anyString()))
                    .thenReturn(false);

            // Act
            Product created = service.createProduct(createCommand());

            // Assert
            assertThat(created.getSku()).isEqualTo(SKU);
            assertThat(created.requireBaseUnit().getUnit()).isEqualTo(bottle);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("normaliza el SKU antes de comprobar duplicados")
        void normalizesSkuBeforeDuplicateCheck() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);
            CreateProductCommand command = new CreateProductCommand(ORGANIZATION_ID, null,
                    "  beb-agua-600 ", null, "Agua", null, bottle.id());

            // Act
            service.createProduct(command);

            // Assert
            verify(productRepository).existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU);
        }

        @Test
        @DisplayName("falla si el SKU ya está en uso y no guarda nada")
        void failsOnDuplicateSku() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.createProduct(createCommand()))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining(SKU);
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("falla si el código de barras ya está en uso")
        void failsOnDuplicateBarcode() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);
            when(productRepository.existsByOrganizationIdAndBarcode(ORGANIZATION_ID, "7701234567890"))
                    .thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.createProduct(createCommand()))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("código de barras");
        }

        @Test
        @DisplayName("no comprueba duplicidad de código de barras cuando no se informa")
        void skipsBarcodeCheckWhenAbsent() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);
            CreateProductCommand command = new CreateProductCommand(ORGANIZATION_ID, null, SKU,
                    "  ", "Agua", null, bottle.id());

            // Act
            service.createProduct(command);

            // Assert
            verify(productRepository, never()).existsByOrganizationIdAndBarcode(any(), any());
        }

        @Test
        @DisplayName("falla si la unidad base no existe")
        void failsWhenBaseUnitMissing() {
            // Arrange
            UUID unknownUnitId = UUID.randomUUID();
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);
            when(unitRepository.findById(unknownUnitId)).thenReturn(Optional.empty());
            CreateProductCommand command = new CreateProductCommand(ORGANIZATION_ID, null, SKU,
                    null, "Agua", null, unknownUnitId);

            // Act & Assert
            assertThatThrownBy(() -> service.createProduct(command))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("unidad de medida");
        }

        @Test
        @DisplayName("rechaza clasificar en una categoría de otra organización")
        void rejectsCategoryFromAnotherOrganization() {
            // Arrange
            Category foreignCategory = Category.reconstitute(CATEGORY_ID, UUID.randomUUID(), "BEB",
                    "Bebidas", null, true, Instant.now(), Instant.now());
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(foreignCategory));
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.createProduct(createCommand()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("otra organización");
        }

        @Test
        @DisplayName("rechaza clasificar en una categoría dada de baja")
        void rejectsInactiveCategory() {
            // Arrange
            category.deactivate();
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.createProduct(createCommand()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("dada de baja");
        }

        @Test
        @DisplayName("falla si la organización no existe, sin consultar productos")
        void failsWhenOrganizationMissing() {
            // Arrange
            when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.createProduct(createCommand()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Presentaciones")
    class Units {

        @Test
        @DisplayName("añade una presentación al producto")
        void addsUnit() {
            // Arrange
            Product product = existingProduct();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

            // Act
            Product result = service.addUnit(
                    new AddProductUnitCommand(PRODUCT_ID, box.id(), new BigDecimal("24")));

            // Assert
            assertThat(result.getUnits()).hasSize(2);
            assertThat(result.hasUnit(box.id())).isTrue();
        }

        @Test
        @DisplayName("falla si la unidad de medida no existe")
        void failsWhenUnitMissing() {
            // Arrange
            UUID unknownUnitId = UUID.randomUUID();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existingProduct()));
            when(unitRepository.findById(unknownUnitId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.addUnit(
                    new AddProductUnitCommand(PRODUCT_ID, unknownUnitId, BigDecimal.TEN)))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Modificación")
    class Modification {

        @Test
        @DisplayName("no comprueba duplicidad si el código de barras no cambia")
        void skipsBarcodeCheckWhenUnchanged() {
            // Arrange
            Product product = existingProduct();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

            // Act
            service.updateProduct(new UpdateProductCommand(PRODUCT_ID, CATEGORY_ID,
                    "7701234567890", "Agua mineral 600 ml", null));

            // Assert
            verify(productRepository, never()).existsByOrganizationIdAndBarcode(any(), any());
        }

        @Test
        @DisplayName("comprueba duplicidad cuando el código de barras cambia")
        void checksBarcodeWhenChanged() {
            // Arrange
            Product product = existingProduct();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productRepository.existsByOrganizationIdAndBarcode(ORGANIZATION_ID, "7709999999999"))
                    .thenReturn(false);

            // Act
            service.updateProduct(new UpdateProductCommand(PRODUCT_ID, CATEGORY_ID,
                    "7709999999999", "Agua mineral 600 ml", null));

            // Assert
            verify(productRepository).existsByOrganizationIdAndBarcode(ORGANIZATION_ID, "7709999999999");
        }

        @Test
        @DisplayName("dar de baja el producto conserva su identidad")
        void deactivateKeepsIdentity() {
            // Arrange
            Product product = existingProduct();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

            // Act
            Product result = service.deactivateProduct(PRODUCT_ID);

            // Assert
            assertThat(result.isActive()).isFalse();
            assertThat(result.getSku()).isEqualTo(SKU);
        }

        @Test
        @DisplayName("falla si el producto no existe")
        void failsWhenProductMissing() {
            // Arrange
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.deactivateProduct(PRODUCT_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Consulta")
    class Query {

        @Test
        @DisplayName("normaliza el SKU antes de buscar por él")
        void normalizesSkuOnLookup() {
            // Arrange
            Product product = existingProduct();
            when(productRepository.findByOrganizationIdAndSku(ORGANIZATION_ID, SKU))
                    .thenReturn(Optional.of(product));

            // Act
            Product found = service.getProductBySku(ORGANIZATION_ID, "  beb-agua-600 ");

            // Assert
            assertThat(found).isEqualTo(product);
            verify(productRepository).findByOrganizationIdAndSku(ORGANIZATION_ID, SKU);
        }

        @Test
        @DisplayName("falla cuando el SKU no corresponde a ningún producto")
        void failsWhenSkuNotFound() {
            // Arrange
            when(productRepository.findByOrganizationIdAndSku(ORGANIZATION_ID, SKU))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.getProductBySku(ORGANIZATION_ID, SKU))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(SKU);
        }

        @Test
        @DisplayName("guarda el producto con el código de barras normalizado")
        void savesNormalizedBarcode() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);
            CreateProductCommand command = new CreateProductCommand(ORGANIZATION_ID, null, SKU,
                    "  7701234567890  ", "Agua", null, bottle.id());

            // Act
            service.createProduct(command);

            // Assert
            ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(saved.capture());
            assertThat(saved.getValue().getBarcode()).isEqualTo("7701234567890");
        }
    }
}
