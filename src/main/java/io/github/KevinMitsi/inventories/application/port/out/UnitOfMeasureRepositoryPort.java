package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitOfMeasureRepositoryPort {

    UnitOfMeasure save(UnitOfMeasure unit);

    Optional<UnitOfMeasure> findById(UUID id);

    Optional<UnitOfMeasure> findByCode(String code);

    boolean existsByCode(String code);

    List<UnitOfMeasure> findAll();
}
