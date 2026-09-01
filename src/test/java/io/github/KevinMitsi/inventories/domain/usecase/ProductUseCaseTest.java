package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.AddProductVariantCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateProductCommand;
import io.github.KevinMitsi.inventories.application.port.in.result.ProductFamily;
import io.github.KevinMitsi.inventories.application.port.out.CategoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UnitOfMeasureRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
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

import java.time.Instant;
import java.util.List;
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
@DisplayName("ProductUseCase")
class ProductUseCaseTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String SKU = "BEB-BRISA-BOT-1L";
    private static final String VARIANT_SKU = "BEB-BRISA-BOL-24";

    @Mock
    private ProductRepositoryPort productRepository;
    @Mock
    private CategoryRepositoryPort categoryRepository;
    @Mock
    private UnitOfMeasureRepositoryPort unitRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;

    @InjectMocks
    private ProductUseCase service;

    private UnitOfMeasure bottle;
    private UnitOfMeasure bag;
    private Category category;

    @BeforeEach
    void setUp() {
        bottle = UnitOfMeasure.create("UNIT", "Unidad", "und");
        bag = UnitOfMeasure.create("PACK", "Paquete", "paq");
        category = Category.reconstitute(CATEGORY_ID, ORGANIZATION_ID, "BEB", "Bebidas",
                null, true, Instant.now(), Instant.now());

        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(unitRepository.findById(bottle.id())).thenReturn(Optional.of(bottle));
        when(unitRepository.findById(bag.id())).thenReturn(Optional.of(bag));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));
    }

    private CreateProductCommand createCommand(List<CreateProductCommand.Variant> variants) {
        return new CreateProductCommand(ORGANIZATION_ID, CATEGORY_ID, SKU, "7701234567890",
                "Agua Brisa Botella 1 L", "Botella PET", bottle.id(), variants);
    }

    private CreateProductCommand createCommand() {
        return createCommand(List.of());
    }

    private CreateProductCommand.Variant variantCommand(String sku, String barcode) {
        return new CreateProductCommand.Variant(sku, barcode, "Agua Brisa Bolsa x 24",
                null, null, bag.id());
    }

    private Product existingProduct() {
        return Product.create(ORGANIZATION_ID, CATEGORY_ID, SKU, "7701234567890",
                "Agua Brisa Botella 1 L", null, bottle);
    }

    @Nested
    @DisplayName("Alta")
    class Creation {

        @Test
        @DisplayName("crea el producto con su unidad y sin variantes")
        void createsProductWithUnit() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);
            when(productRepository.existsByOrganizationIdAndBarcode(eq(ORGANIZATION_ID), anyString()))
                    .thenReturn(false);

            // Act
            ProductFamily created = service.createProduct(createCommand());

            // Assert
            assertThat(created.principal().getSku()).isEqualTo(SKU);
            assertThat(created.principal().getUnit()).isEqualTo(bottle);
            assertThat(created.principal().isVariant()).isFalse();
            assertThat(created.variants()).isEmpty();
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("crea las variantes junto al principal, cada una como producto propio")
        void createsVariantsAlongsidePrincipal() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(any(), anyString())).thenReturn(false);
            when(productRepository.existsByOrganizationIdAndBarcode(any(), anyString())).thenReturn(false);

            // Act
            ProductFamily created = service.createProduct(
                    createCommand(List.of(variantCommand(VARIANT_SKU, "7709999999999"))));

            // Assert
            assertThat(created.variants()).hasSize(1);
            Product variant = created.variants().getFirst();
            assertThat(variant.getSku()).isEqualTo(VARIANT_SKU);
            assertThat(variant.getParentProductId()).isEqualTo(created.principal().getId());
            assertThat(variant.getUnit()).isEqualTo(bag);
            verify(productRepository, org.mockito.Mockito.times(2)).save(any(Product.class));
        }

        @Test
        @DisplayName("detecta el SKU repetido entre dos variantes de la misma petición")
        void detectsDuplicateSkuWithinSameRequest() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(any(), anyString())).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.createProduct(createCommand(List.of(
                    variantCommand(VARIANT_SKU, null),
                    variantCommand(VARIANT_SKU, null)))))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining(VARIANT_SKU);
        }

        @Test
        @DisplayName("no guarda nada si una variante es inválida")
        void savesNothingWhenAVariantIsInvalid() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(any(), anyString())).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.createProduct(createCommand(List.of(
                    new CreateProductCommand.Variant(VARIANT_SKU, null, "  ", null, null, bag.id())))))
                    .isInstanceOf(DomainValidationException.class);
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("normaliza el SKU antes de comprobar duplicados")
        void normalizesSkuBeforeDuplicateCheck() {
            // Arrange
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);
            CreateProductCommand command = new CreateProductCommand(ORGANIZATION_ID, null,
                    "  beb-brisa-bot-1l ", null, "Agua", null, bottle.id(), null);

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
                    "  ", "Agua", null, bottle.id(), null);

            // Act
            service.createProduct(command);

            // Assert
            verify(productRepository, never()).existsByOrganizationIdAndBarcode(any(), any());
        }

        @Test
        @DisplayName("falla si la unidad de medida no existe")
        void failsWhenUnitMissing() {
            // Arrange
            UUID unknownUnitId = UUID.randomUUID();
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, SKU)).thenReturn(false);
            when(unitRepository.findById(unknownUnitId)).thenReturn(Optional.empty());
            CreateProductCommand command = new CreateProductCommand(ORGANIZATION_ID, null, SKU,
                    null, "Agua", null, unknownUnitId, null);

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
    @DisplayName("Variantes")
    class Variants {

        @Test
        @DisplayName("cuelga una variante de un producto existente")
        void addsVariant() {
            // Arrange
            Product principal = existingProduct();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(principal));
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, VARIANT_SKU))
                    .thenReturn(false);

            // Act
            Product variant = service.addVariant(new AddProductVariantCommand(PRODUCT_ID, VARIANT_SKU,
                    null, "Agua Brisa Bolsa x 24", null, null, bag.id()));

            // Assert
            assertThat(variant.getParentProductId()).isEqualTo(principal.getId());
            assertThat(variant.getUnit()).isEqualTo(bag);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("la variante hereda la unidad del principal si no se indica otra")
        void variantInheritsUnit() {
            // Arrange
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existingProduct()));

            // Act
            Product variant = service.addVariant(new AddProductVariantCommand(PRODUCT_ID, VARIANT_SKU,
                    null, "Agua Brisa Botella 1 L con gas", null, null, null));

            // Assert
            assertThat(variant.getUnit()).isEqualTo(bottle);
        }

        @Test
        @DisplayName("rechaza colgar una variante de otra variante")
        void rejectsNestedVariant() {
            // Arrange
            Product variant = existingProduct().createVariant(VARIANT_SKU, null,
                    "Agua Brisa Bolsa x 24", null, null, bag);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(variant));

            // Act & Assert
            assertThatThrownBy(() -> service.addVariant(new AddProductVariantCommand(PRODUCT_ID,
                    "OTRO-SKU", null, "Otra", null, null, bag.id())))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("un solo nivel");
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("falla si el SKU de la variante ya está en uso")
        void rejectsDuplicateVariantSku() {
            // Arrange
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existingProduct()));
            when(productRepository.existsByOrganizationIdAndSku(ORGANIZATION_ID, VARIANT_SKU))
                    .thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.addVariant(new AddProductVariantCommand(PRODUCT_ID,
                    VARIANT_SKU, null, "Agua Brisa Bolsa x 24", null, null, bag.id())))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("la familia de un principal trae sus variantes")
        void familyOfPrincipalCarriesVariants() {
            // Arrange
            Product principal = existingProduct();
            Product variant = principal.createVariant(VARIANT_SKU, null, "Agua Brisa Bolsa x 24",
                    null, null, bag);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(principal));
            when(productRepository.findVariants(PRODUCT_ID)).thenReturn(List.of(variant));

            // Act
            ProductFamily family = service.getProductFamily(PRODUCT_ID);

            // Assert
            assertThat(family.principal()).isEqualTo(principal);
            assertThat(family.variants()).containsExactly(variant);
        }

        @Test
        @DisplayName("la familia de una variante llega vacía y no consulta hijos")
        void familyOfVariantIsEmpty() {
            // Arrange
            Product variant = existingProduct().createVariant(VARIANT_SKU, null,
                    "Agua Brisa Bolsa x 24", null, null, bag);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(variant));

            // Act
            ProductFamily family = service.getProductFamily(PRODUCT_ID);

            // Assert
            assertThat(family.variants()).isEmpty();
            verify(productRepository, never()).findVariants(any());
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
                    "7701234567890", "Agua Brisa Botella 1 L", null));

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
                    "7709999999999", "Agua Brisa Botella 1 L", null));

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
            Product found = service.getProductBySku(ORGANIZATION_ID, "  beb-brisa-bot-1l ");

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
                    "  7701234567890  ", "Agua", null, bottle.id(), null);

            // Act
            service.createProduct(command);

            // Assert
            ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(saved.capture());
            assertThat(saved.getValue().getBarcode()).isEqualTo("7701234567890");
        }
    }
}
