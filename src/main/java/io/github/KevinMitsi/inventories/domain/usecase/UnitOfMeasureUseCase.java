package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.QueryUnitOfMeasureUseCase;
import io.github.KevinMitsi.inventories.application.port.out.UnitOfMeasureRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;

import java.util.List;
import java.util.UUID;

public class UnitOfMeasureUseCase implements QueryUnitOfMeasureUseCase {

    private static final String UNIT = "la unidad de medida";

    private final UnitOfMeasureRepositoryPort unitRepository;

    public UnitOfMeasureUseCase(UnitOfMeasureRepositoryPort unitRepository) {
        this.unitRepository = unitRepository;
    }

    @Override
    public UnitOfMeasure getUnitById(UUID unitId) {
        return unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException(UNIT, unitId));
    }

    @Override
    public List<UnitOfMeasure> getAllUnits() {
        return unitRepository.findAll();
    }
}
