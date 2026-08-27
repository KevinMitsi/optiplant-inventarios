package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.out.RoleRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.UserPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador de salida que satisface {@link RoleRepositoryPort} con JPA. */
@Component
@RequiredArgsConstructor
public class RolePersistenceAdapter implements RoleRepositoryPort {

    private final RoleJpaRepository repository;
    private final UserPersistenceMapper mapper;

    @Override
    public Optional<Role> findById(UUID id) {
        return repository.findById(id).map(mapper::toRoleDomain);
    }

    @Override
    public Optional<Role> findByCode(RoleCode code) {
        return repository.findByCode(code.name()).map(mapper::toRoleDomain);
    }

    @Override
    public List<Role> findAll() {
        return repository.findAll().stream().map(mapper::toRoleDomain).toList();
    }
}
