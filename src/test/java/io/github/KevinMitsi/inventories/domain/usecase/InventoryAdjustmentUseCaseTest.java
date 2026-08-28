package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateInventoryAdjustmentCommand;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAdjustmentRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustment;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustmentItem;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.Product;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InventoryAdjustmentUseCase")
class InventoryAdjustmentServiceTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID ADJUSTMENT_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();

    @Mock
    private InventoryAdjustmentRepositoryPort adjustmentRepository;
    @Mock
    private BranchRepositoryPort branchRepository;
    @Mock
    private ProductRepositoryPort productRepository;
    @Mock
    private InventoryMovementPoster poster;

    private InventoryAdjustmentUseCase service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new InventoryAdjustmentUseCase(adjustmentRepository, branchRepository, productRepository, poster);

        product = Product.create(UUID.randomUUID(), null, "SKU-1", null, "Producto", null,
                new UnitOfMeasure(UUID.randomUUID(), "UNIT", "Unidad", "und"));

        when(branchRepository.existsById(BRANCH_ID)).thenReturn(true);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(adjustmentRepository.save(any(InventoryAdjustment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private CreateInventoryAdjustmentCommand commandWithDelta(BigDecimal delta) {
        return new CreateInventoryAdjustmentCommand(BRANCH_ID, CREATED_BY, "Conteo físico",
                List.of(new CreateInventoryAdjustmentCommand.Item(PRODUCT_ID, delta, null)));
    }

    @Nested
    @DisplayName("Creación")
    class Creation {

        @Test
        @DisplayName("crea el ajuste sin mover stock")
        void createsWithoutTouchingStock() {
            // Act
            InventoryAdjustment adjustment = service.createAdjustment(commandWithDelta(new BigDecimal("-2")));

            // Assert
            assertThat(adjustment.isApproved()).isFalse();
            verifyNoInteractions(poster);
        }

        @Test
        @DisplayName("falla si la sucursal no existe")
        void failsWhenBranchMissing() {
            when(branchRepository.existsById(BRANCH_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.createAdjustment(commandWithDelta(BigDecimal.ONE)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("falla si algún producto de las líneas no existe")
        void failsWhenProductMissing() {
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createAdjustment(commandWithDelta(BigDecimal.ONE)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Aprobación (§18.2)")
    class Approval {

        @Test
        @DisplayName("aprobar postea un movimiento ADJUSTMENT_OUT por una línea negativa")
        void postsAdjustmentOutForNegativeDelta() {
            // Arrange
            InventoryAdjustment adjustment = InventoryAdjustment.create(BRANCH_ID, CREATED_BY, "Conteo físico",
                    List.of(InventoryAdjustmentItem.create(PRODUCT_ID, new BigDecimal("-4"), null)));
            when(adjustmentRepository.findById(ADJUSTMENT_ID)).thenReturn(Optional.of(adjustment));

            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);

            // Act
            UUID approver = UUID.randomUUID();
            service.approveAdjustment(ADJUSTMENT_ID, approver);

            // Assert
            verify(poster).post(captor.capture());
            assertThat(captor.getValue().movementType()).isEqualTo(InventoryMovementType.ADJUSTMENT_OUT);
            assertThat(captor.getValue().quantity()).isEqualByComparingTo(new BigDecimal("4"));
            assertThat(captor.getValue().adjustmentId()).isEqualTo(adjustment.getId());
        }

        @Test
        @DisplayName("aprobar postea un movimiento ADJUSTMENT_IN por una línea positiva")
        void postsAdjustmentInForPositiveDelta() {
            // Arrange
            InventoryAdjustment adjustment = InventoryAdjustment.create(BRANCH_ID, CREATED_BY, "Conteo físico",
                    List.of(InventoryAdjustmentItem.create(PRODUCT_ID, new BigDecimal("6"), null)));
            when(adjustmentRepository.findById(ADJUSTMENT_ID)).thenReturn(Optional.of(adjustment));

            ArgumentCaptor<PostInventoryMovementCommand> captor =
                    ArgumentCaptor.forClass(PostInventoryMovementCommand.class);

            // Act
            service.approveAdjustment(ADJUSTMENT_ID, UUID.randomUUID());

            // Assert
            verify(poster).post(captor.capture());
            assertThat(captor.getValue().movementType()).isEqualTo(InventoryMovementType.ADJUSTMENT_IN);
        }

        @Test
        @DisplayName("una línea por producto: N líneas postean N movimientos")
        void postsOneMovementPerItem() {
            // Arrange
            InventoryAdjustment adjustment = InventoryAdjustment.create(BRANCH_ID, CREATED_BY, "Conteo físico",
                    List.of(
                            InventoryAdjustmentItem.create(PRODUCT_ID, new BigDecimal("3"), null),
                            InventoryAdjustmentItem.create(PRODUCT_ID, new BigDecimal("-1"), null)));
            when(adjustmentRepository.findById(ADJUSTMENT_ID)).thenReturn(Optional.of(adjustment));

            // Act
            service.approveAdjustment(ADJUSTMENT_ID, UUID.randomUUID());

            // Assert
            verify(poster, times(2)).post(any());
        }

        @Test
        @DisplayName("no se puede aprobar un ajuste que ya fue aprobado")
        void cannotApproveTwice() {
            // Arrange
            InventoryAdjustment adjustment = InventoryAdjustment.create(BRANCH_ID, CREATED_BY, "Conteo físico",
                    List.of(InventoryAdjustmentItem.create(PRODUCT_ID, new BigDecimal("1"), null)));
            adjustment.approve(UUID.randomUUID());
            when(adjustmentRepository.findById(ADJUSTMENT_ID)).thenReturn(Optional.of(adjustment));

            // Act & Assert
            assertThatThrownBy(() -> service.approveAdjustment(ADJUSTMENT_ID, UUID.randomUUID()))
                    .isInstanceOf(io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException.class);

            verify(poster, never()).post(any());
        }
    }
}
