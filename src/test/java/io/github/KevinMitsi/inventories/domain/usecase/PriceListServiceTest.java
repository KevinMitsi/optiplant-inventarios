package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.port.in.command.CreatePriceListCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetProductPriceCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdatePriceListCommand;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PriceListRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductPriceRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.PriceList;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.ProductPrice;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Cubre CRUD de listas de precios y el upsert de precio por producto (HU-25, RF-29). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PriceListUseCase")
class PriceListServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Mock
    private PriceListRepositoryPort priceListRepository;
    @Mock
    private ProductPriceRepositoryPort productPriceRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;
    @Mock
    private ProductRepositoryPort productRepository;

    private PriceListUseCase service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new PriceListUseCase(priceListRepository, productPriceRepository, organizationRepository,
                productRepository);

        UnitOfMeasure unit = new UnitOfMeasure(UUID.randomUUID(), "UNIT", "Unidad", "und");
        product = Product.create(UUID.randomUUID(), null, "SKU-1", null, "Producto", null, unit);

        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(priceListRepository.save(any(PriceList.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    @DisplayName("Creación")
    class Creation {

        @Test
        @DisplayName("crea la lista cuando el código no está en uso")
        void createsPriceList() {
            PriceList created = service.createPriceList(new CreatePriceListCommand(ORGANIZATION_ID, "MINORISTA",
                    "Minorista", null, null, null));

            assertThat(created.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(created.getCode()).isEqualTo("MINORISTA");
            assertThat(created.isActive()).isTrue();
        }

        @Test
        @DisplayName("rechaza un código duplicado en la misma organización")
        void rejectsDuplicateCode() {
            when(priceListRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "MINORISTA")).thenReturn(true);

            assertThatThrownBy(() -> service.createPriceList(new CreatePriceListCommand(ORGANIZATION_ID,
                    "MINORISTA", "Minorista", null, null, null)))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("Actualización y activación")
    class UpdateAndActivation {

        @Test
        @DisplayName("actualiza nombre, descripción y vigencia")
        void updatesDetails() {
            PriceList priceList = PriceList.create(ORGANIZATION_ID, "MINORISTA", "Minorista", null, null, null);
            when(priceListRepository.findById(priceList.getId())).thenReturn(Optional.of(priceList));

            PriceList updated = service.updatePriceList(new UpdatePriceListCommand(priceList.getId(),
                    "Minorista actualizado", "desc", null, null));

            assertThat(updated.getName()).isEqualTo("Minorista actualizado");
        }

        @Test
        @DisplayName("desactivar y reactivar cambian el estado")
        void deactivateThenActivate() {
            PriceList priceList = PriceList.create(ORGANIZATION_ID, "MINORISTA", "Minorista", null, null, null);
            when(priceListRepository.findById(priceList.getId())).thenReturn(Optional.of(priceList));

            PriceList deactivated = service.deactivatePriceList(priceList.getId());
            assertThat(deactivated.isActive()).isFalse();

            PriceList activated = service.activatePriceList(priceList.getId());
            assertThat(activated.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Precio por producto")
    class ProductPriceUpsert {

        @Test
        @DisplayName("crea el precio cuando no existía uno para esa presentación")
        void createsProductPriceWhenAbsent() {
            PriceList priceList = PriceList.create(ORGANIZATION_ID, "MINORISTA", "Minorista", null, null, null);
            when(priceListRepository.findById(priceList.getId())).thenReturn(Optional.of(priceList));
            when(productPriceRepository.save(any(ProductPrice.class))).thenAnswer(call -> call.getArgument(0));

            ProductPrice saved = service.setProductPrice(new SetProductPriceCommand(priceList.getId(),
                    product.getId(), product.requireBaseUnit().getId(), new BigDecimal("100.00")));

            assertThat(saved.getPrice()).isEqualTo(Money.of("100.00"));
        }

        @Test
        @DisplayName("reemplaza el precio existente en lugar de duplicarlo")
        void replacesExistingProductPrice() {
            PriceList priceList = PriceList.create(ORGANIZATION_ID, "MINORISTA", "Minorista", null, null, null);
            when(priceListRepository.findById(priceList.getId())).thenReturn(Optional.of(priceList));

            ProductPrice existing = ProductPrice.create(priceList.getId(), product.getId(),
                    product.requireBaseUnit().getId(), Money.of("100.00"));
            when(productPriceRepository.findByPriceListIdAndProductIdAndProductUnitId(
                    priceList.getId(), product.getId(), product.requireBaseUnit().getId()))
                    .thenReturn(Optional.of(existing));
            when(productPriceRepository.save(any(ProductPrice.class))).thenAnswer(call -> call.getArgument(0));

            ProductPrice saved = service.setProductPrice(new SetProductPriceCommand(priceList.getId(),
                    product.getId(), product.requireBaseUnit().getId(), new BigDecimal("120.00")));

            assertThat(saved.getId()).isEqualTo(existing.getId());
            assertThat(saved.getPrice()).isEqualTo(Money.of("120.00"));
        }
    }
}
