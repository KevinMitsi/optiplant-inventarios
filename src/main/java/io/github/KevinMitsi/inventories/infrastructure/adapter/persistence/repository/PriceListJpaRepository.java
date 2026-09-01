package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.PriceListJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PriceListJpaRepository extends JpaRepository<PriceListJpaEntity, UUID>,
                                                 JpaSpecificationExecutor<PriceListJpaEntity> {

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
