package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateInventoryAdjustmentCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryEntryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.RegisterInventoryExitCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetMinimumStockCommand;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustment;
import io.github.KevinMitsi.inventories.domain.model.InventoryAlert;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.InventoryAdjustmentDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.InventoryAlertDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.InventoryDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Traduce el módulo de inventario entre el contrato HTTP y la capa de aplicación.
 *
 * <p>Los métodos que envuelven/desenvuelven {@link Quantity} y {@link Money} son
 * cualificadores implícitos: MapStruct los usa automáticamente para resolver el desajuste
 * de tipo entre el {@code BigDecimal} de los DTO y los value objects del dominio, sin que
 * cada mapeo tenga que declararlo campo por campo.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InventoryWebMapper {

    @Mapping(target = "branchId", source = "branchId")
    @Mapping(target = "productId", source = "request.productId")
    @Mapping(target = "quantity", source = "request.quantity")
    @Mapping(target = "reason", source = "request.reason")
    @Mapping(target = "userId", source = "userId")
    RegisterInventoryEntryCommand toEntryCommand(UUID branchId, InventoryDtos.RegisterInventoryMovementRequest request,
                                                 UUID userId);

    @Mapping(target = "branchId", source = "branchId")
    @Mapping(target = "productId", source = "request.productId")
    @Mapping(target = "quantity", source = "request.quantity")
    @Mapping(target = "reason", source = "request.reason")
    @Mapping(target = "userId", source = "userId")
    RegisterInventoryExitCommand toExitCommand(UUID branchId, InventoryDtos.RegisterInventoryMovementRequest request,
                                               UUID userId);

    default SetMinimumStockCommand toCommand(UUID branchId, UUID productId,
                                             InventoryDtos.SetMinimumStockRequest request) {
        return new SetMinimumStockCommand(branchId, productId, request.minimumStock());
    }

    @Mapping(target = "lowStock", source = "inventory.lowStock")
    @Mapping(target = "outOfStock", source = "inventory.outOfStock")
    InventoryDtos.InventoryResponse toResponse(Inventory inventory);

    @Mapping(target = "direction", expression = "java(movement.getMovementType().direction().name())")
    InventoryDtos.InventoryMovementResponse toResponse(InventoryMovement movement);

    @Mapping(target = "branchId", source = "branchId")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "reason", source = "request.reason")
    @Mapping(target = "items", source = "request.items")
    CreateInventoryAdjustmentCommand toCommand(UUID branchId, UUID createdBy,
                                               InventoryAdjustmentDtos.CreateInventoryAdjustmentRequest request);

    CreateInventoryAdjustmentCommand.Item toItem(InventoryAdjustmentDtos.CreateInventoryAdjustmentRequest.ItemRequest item);

    @Mapping(target = "approved", source = "adjustment.approved")
    InventoryAdjustmentDtos.InventoryAdjustmentResponse toResponse(InventoryAdjustment adjustment);

    InventoryAdjustmentDtos.InventoryAdjustmentResponse.ItemResponse toResponse(
            io.github.KevinMitsi.inventories.domain.model.InventoryAdjustmentItem item);

    InventoryAlertDtos.InventoryAlertResponse toResponse(InventoryAlert alert);

    default BigDecimal map(Quantity quantity) {
        return quantity == null ? null : quantity.value();
    }

    default BigDecimal map(Money money) {
        return money == null ? null : money.amount();
    }
}
