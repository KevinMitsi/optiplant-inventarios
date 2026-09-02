package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateSaleCommand;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PriceListRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductPriceRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.SaleRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.InsufficientStockException;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.Percentage;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.Sale;
import io.github.KevinMitsi.inventories.domain.model.SaleItem;
import io.github.KevinMitsi.inventories.domain.model.SaleStatus;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre RN-03 (no confirmar una venta por encima del stock disponible) y la restitución de
 * inventario al cancelar una venta ya confirmada.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SaleUseCase")
class SaleServiceTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private SaleRepositoryPort saleRepository;
    @Mock
    private BranchRepositoryPort branchRepository;
    @Mock
    private ProductRepositoryPort productRepository;
    @Mock
    private PriceListRepositoryPort priceListRepository;
    @Mock
    private ProductPriceRepositoryPort productPriceRepository;
    @Mock
    private InventoryMovementPoster poster;

    private SaleUseCase service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new SaleUseCase(saleRepository, branchRepository, productRepository, priceListRepository,
                productPriceRepository, poster);

        UnitOfMeasure unit = new UnitOfMeasure(UUID.randomUUID(), "UNIT", "Unidad", "und");
        product = Product.create(UUID.randomUUID(), null, "SKU-1", null, "Producto", null, unit);

        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(saleRepository.save(any(Sale.class))).thenAnswer(call -> call.getArgument(0));
    }

    private Sale draftSale() {
        SaleItem item = SaleItem.create(product.getId(), Quantity.of("10"),
                Money.of("50.00"), Percentage.ZERO);
        return Sale.create(BRANCH_ID, USER_ID, null, "V-0001", Instant.now(), null, List.of(item));
    }

    @Nested
    @DisplayName("Confirmación")
    class Confirmation {

        @Test
        @DisplayName("RN-03: propaga InsufficientStockException cuando no hay stock suficiente")
        void propagatesInsufficientStockFromPoster() {
            Sale sale = draftSale();
            when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

            doThrow(new InsufficientStockException(BRANCH_ID, product.getId(), product.getSku(),
                    new java.math.BigDecimal("10"), new java.math.BigDecimal("3")))
                    .when(poster).post(any(PostInventoryMovementCommand.class));

            assertThatThrownBy(() -> service.confirmSale(sale.getId()))
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test
        @DisplayName("postea SALE_OUT por cada línea al confirmar")
        void postsSaleOutPerItem() {
            Sale sale = draftSale();
            when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);

            Sale result = service.confirmSale(sale.getId());

            verify(poster).post(captor.capture());
            assertThat(captor.getValue().movementType()).isEqualTo(InventoryMovementType.SALE_OUT);
            assertThat(captor.getValue().quantity()).isEqualByComparingTo("10");
            assertThat(result.getStatus()).isEqualTo(SaleStatus.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("Cancelación")
    class Cancellation {

        @Test
        @DisplayName("cancelar una venta CONFIRMED postea RETURN_IN compensatorio por cada línea")
        void cancellingConfirmedSaleRestocks() {
            Sale sale = draftSale();
            sale.confirm();
            when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);

            Sale result = service.cancelSale(sale.getId());

            verify(poster, times(1)).post(captor.capture());
            assertThat(captor.getValue().movementType()).isEqualTo(InventoryMovementType.RETURN_IN);
            assertThat(captor.getValue().quantity()).isEqualByComparingTo("10");
            assertThat(result.getStatus()).isEqualTo(SaleStatus.CANCELLED);
        }

        @Test
        @DisplayName("cancelar una venta DRAFT no toca inventario")
        void cancellingDraftSaleDoesNotTouchInventory() {
            Sale sale = draftSale();
            when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

            service.cancelSale(sale.getId());

            verify(poster, times(0)).post(any(PostInventoryMovementCommand.class));
        }
    }
}
