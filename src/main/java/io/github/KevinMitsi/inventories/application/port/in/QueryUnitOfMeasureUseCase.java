package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;

import java.util.List;
import java.util.UUID;

/** Consulta del catalogo global de unidades de medida. */
public interface QueryUnitOfMeasureUseCase {

    UnitOfMeasure getUnitById(UUID unitId);

    List<UnitOfMeasure> getAllUnits();
}
