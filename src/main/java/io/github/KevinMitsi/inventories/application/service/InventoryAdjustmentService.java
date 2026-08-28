package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryAdjustmentUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateInventoryAdjustmentCommand;
import io.github.KevinMitsi.inventories.domain.model.InventoryAdjustment;
import io.github.KevinMitsi.inventories.domain.usecase.InventoryAdjustmentUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Primary
@Service
@Transactional(rollbackFor = Exception.class)
public class InventoryAdjustmentService implements ManageInventoryAdjustmentUseCase {

    private final InventoryAdjustmentUseCase useCase;

    public InventoryAdjustmentService(InventoryAdjustmentUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public InventoryAdjustment createAdjustment(CreateInventoryAdjustmentCommand command) {
        return useCase.createAdjustment(command);
    }

    @Override
    public InventoryAdjustment approveAdjustment(UUID adjustmentId, UUID approvedBy) {
        return useCase.approveAdjustment(adjustmentId, approvedBy);
    }

    @Override
    public InventoryAdjustment getAdjustmentById(UUID adjustmentId) {
        return useCase.getAdjustmentById(adjustmentId);
    }
}
