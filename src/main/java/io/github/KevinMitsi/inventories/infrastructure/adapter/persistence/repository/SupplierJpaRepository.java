package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.SupplierJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SupplierJpaRepository extends JpaRepository<SupplierJpaEntity, UUID>,
                                                JpaSpecificationExecutor<SupplierJpaEntity> {

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
