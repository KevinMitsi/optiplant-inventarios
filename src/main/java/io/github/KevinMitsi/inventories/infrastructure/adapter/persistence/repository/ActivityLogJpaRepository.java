package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ActivityLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ActivityLogJpaRepository extends JpaRepository<ActivityLogJpaEntity, UUID>,
                                                  JpaSpecificationExecutor<ActivityLogJpaEntity> {
}
