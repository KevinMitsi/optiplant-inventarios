package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.OrganizationPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.OrganizationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Adaptador de salida que satisface {@link OrganizationRepositoryPort} con JPA. */
@Component
@RequiredArgsConstructor
public class OrganizationPersistenceAdapter implements OrganizationRepositoryPort {

    private final OrganizationJpaRepository repository;
    private final OrganizationPersistenceMapper mapper;

    @Override
    public Organization save(Organization organization) {
        return mapper.toDomain(repository.save(mapper.toEntity(organization)));
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Organization> findByCode(String code) {
        return repository.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }
}
