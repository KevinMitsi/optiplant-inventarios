package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.QueryUnitOfMeasureUseCase;
import io.github.KevinMitsi.inventories.application.port.out.UnitOfMeasureRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UnitOfMeasureService implements QueryUnitOfMeasureUseCase {

    private static final String UNIT = "la unidad de medida";

    private final UnitOfMeasureRepositoryPort unitRepository;

    public UnitOfMeasureService(UnitOfMeasureRepositoryPort unitRepository) {
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
