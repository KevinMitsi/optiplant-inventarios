package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.UserSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UserJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.UserPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.UserJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.UserSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Adaptador de salida que satisface {@link UserRepositoryPort} con JPA. */
@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepositoryPort {

    /**
     * Campos por los que se admite ordenar.
     *
     * <p>Deliberadamente no incluye {@code passwordHash} ni ningún campo sensible: el valor
     * llega de un parámetro de la petición, y ordenar por un hash permitiría inferir
     * información sobre él comparando el orden de los resultados.
     */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("firstName", "lastName", "email", "active", "lastLoginAt", "createdAt");

    private static final String DEFAULT_SORT_FIELD = "lastName";

    private final UserJpaRepository repository;
    private final UserPersistenceMapper mapper;

    @Override
    public User save(User user) {
        return mapper.toDomain(repository.save(mapper.toEntity(user)));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByOrganizationIdAndEmail(UUID organizationId, String email) {
        return repository.findByOrganizationIdAndEmail(organizationId, email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrganizationIdAndEmail(UUID organizationId, String email) {
        return repository.existsByOrganizationIdAndEmail(organizationId, email);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public PageResult<User> search(UserSearchCriteria criteria, PageQuery pageQuery) {
        Page<UserJpaEntity> page = repository.findAll(
                UserSpecifications.from(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }

    @Override
    public long countActiveAdmins(UUID organizationId) {
        return repository.countActiveAdmins(organizationId);
    }
}
