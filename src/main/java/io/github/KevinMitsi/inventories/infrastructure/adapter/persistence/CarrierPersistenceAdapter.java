package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.CarrierSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.CarrierRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Carrier;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.CarrierJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.LogisticsPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.CarrierJpaRepository;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.LogisticsSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CarrierPersistenceAdapter implements CarrierRepositoryPort {

    private static final Set<String> SORTABLE_FIELDS = Set.of("code", "name", "active", "createdAt", "updatedAt");
    private static final String DEFAULT_SORT_FIELD = "name";

    private final CarrierJpaRepository repository;
    private final LogisticsPersistenceMapper mapper;

    @Override
    public Carrier save(Carrier carrier) {
        return mapper.toDomain(repository.save(mapper.toEntity(carrier)));
    }

    @Override
    public Optional<Carrier> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrganizationIdAndCode(UUID organizationId, String code) {
        return repository.existsByOrganizationIdAndCode(organizationId, code);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public PageResult<Carrier> search(CarrierSearchCriteria criteria, PageQuery pageQuery) {
        Page<CarrierJpaEntity> page = repository.findAll(
                LogisticsSpecifications.forCarriers(criteria),
                PageQueryTranslator.toPageable(pageQuery, SORTABLE_FIELDS, DEFAULT_SORT_FIELD));

        return PageQueryTranslator.toPageResult(page, mapper::toDomain);
    }
}
