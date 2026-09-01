package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryEntryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryExitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetMinimumStockCommand;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryMovementRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
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

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InventoryUseCase")
class InventoryServiceTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private InventoryRepositoryPort inventoryRepository;
    @Mock
    private InventoryMovementRepositoryPort movementRepository;
    @Mock
    private BranchRepositoryPort branchRepository;
    @Mock
    private ProductRepositoryPort productRepository;
    @Mock
    private InventoryMovementPoster poster;

    private InventoryUseCase service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new InventoryUseCase(inventoryRepository, movementRepository, branchRepository,
                productRepository, poster);

        product = Product.create(UUID.randomUUID(), null, "SKU-1", null, "Producto",
                null, new UnitOfMeasure(UUID.randomUUID(), "UNIT", "Unidad", "und"));

        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    @DisplayName("Stock mínimo (HU-15)")
    class MinimumStock {

        @Test
        @DisplayName("crea el saldo en cero si nunca hubo movimientos, y fija el mínimo")
        void createsInventoryWhenAbsent() {
            // Arrange
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.empty());

            // Act
            Inventory result = service.setMinimumStock(
                    new SetMinimumStockCommand(BRANCH_ID, PRODUCT_ID, new BigDecimal("10")));

            // Assert
            assertThat(result.getMinimumStock()).isEqualTo(Quantity.of("10"));
            assertThat(result.getQuantity()).isEqualTo(Quantity.ZERO);
        }

        @Test
        @DisplayName("falla si la sucursal no existe")
        void failsWhenBranchMissing() {
            when(branchRepository.existsById(BRANCH_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.setMinimumStock(
                    new SetMinimumStockCommand(BRANCH_ID, PRODUCT_ID, BigDecimal.TEN)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Movimiento manual (HU-12/HU-13)")
    class ManualMovements {

        @Test
        @DisplayName("registrar una entrada delega en el poster como RETURN_IN")
        void registersEntryAsReturnIn() {
            // Arrange
            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);
            when(poster.post(any())).thenReturn(null);

            // Act
            service.registerEntry(new RegisterInventoryEntryCommand(
                    BRANCH_ID, PRODUCT_ID, new BigDecimal("5"), "Devolución", USER_ID));

            // Assert
            verify(poster).post(captor.capture());
            assertThat(captor.getValue().movementType()).isEqualTo(InventoryMovementType.RETURN_IN);
            assertThat(captor.getValue().productSku()).isEqualTo("SKU-1");
        }

        @Test
        @DisplayName("registrar una salida delega en el poster como LOSS_OUT")
        void registersExitAsLossOut() {
            // Arrange
            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);
            when(poster.post(any())).thenReturn(null);

            // Act
            service.registerExit(new RegisterInventoryExitCommand(
                    BRANCH_ID, PRODUCT_ID, new BigDecimal("2"), "Merma", USER_ID));

            // Assert
            verify(poster).post(captor.capture());
            assertThat(captor.getValue().movementType()).isEqualTo(InventoryMovementType.LOSS_OUT);
        }

        @Test
        @DisplayName("falla si el producto no existe, sin llegar a invocar al poster")
        void failsWhenProductMissing() {
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registerEntry(new RegisterInventoryEntryCommand(
                    BRANCH_ID, PRODUCT_ID, BigDecimal.ONE, "Motivo", USER_ID)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(poster, never()).post(any());
        }
    }

    @Nested
    @DisplayName("Consulta")
    class Query {

        @Test
        @DisplayName("falla si nunca hubo saldo para esa pareja sucursal/producto")
        void failsWhenInventoryNeverExisted() {
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByBranchAndProduct(BRANCH_ID, PRODUCT_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("el histórico de movimientos se resuelve por el id del saldo, no por sucursal/producto")
        void movementHistoryResolvesByInventoryId() {
            // Arrange
            Inventory inventory = Inventory.open(BRANCH_ID, PRODUCT_ID);
            when(inventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(inventory));
            when(movementRepository.findByInventoryId(any(), any()))
                    .thenReturn(io.github.KevinMitsi.inventories.domain.model.PageResult.empty(
                            io.github.KevinMitsi.inventories.domain.model.PageQuery.firstPage()));

            // Act
            service.getMovementHistory(BRANCH_ID, PRODUCT_ID,
                    io.github.KevinMitsi.inventories.domain.model.PageQuery.firstPage());

            // Assert
            verify(movementRepository).findByInventoryId(
                    org.mockito.ArgumentMatchers.eq(inventory.getId()), any());
        }
    }
}
