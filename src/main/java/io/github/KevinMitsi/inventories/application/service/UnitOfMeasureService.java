package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.QueryUnitOfMeasureUseCase;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import io.github.KevinMitsi.inventories.domain.usecase.UnitOfMeasureUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class UnitOfMeasureService implements QueryUnitOfMeasureUseCase {

    private final UnitOfMeasureUseCase useCase;

    public UnitOfMeasureService(UnitOfMeasureUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public UnitOfMeasure getUnitById(UUID unitId) {
        return useCase.getUnitById(unitId);
    }

    @Override
    public List<UnitOfMeasure> getAllUnits() {
        return useCase.getAllUnits();
    }
}
