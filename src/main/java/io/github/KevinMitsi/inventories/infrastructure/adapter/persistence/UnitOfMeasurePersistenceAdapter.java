package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.out.UnitOfMeasureRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.CatalogPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.UnitOfMeasureJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UnitOfMeasurePersistenceAdapter implements UnitOfMeasureRepositoryPort {

    private final UnitOfMeasureJpaRepository repository;
    private final CatalogPersistenceMapper mapper;

    @Override
    public UnitOfMeasure save(UnitOfMeasure unit) {
        return mapper.toDomain(repository.save(mapper.toEntity(unit)));
    }

    @Override
    public Optional<UnitOfMeasure> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<UnitOfMeasure> findByCode(String code) {
        return repository.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    public List<UnitOfMeasure> findAll() {
        return repository.findAll(Sort.by("code")).stream().map(mapper::toDomain).toList();
    }
}
