package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository;

import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.UnitOfMeasureJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnitOfMeasureJpaRepository extends JpaRepository<UnitOfMeasureJpaEntity, UUID> {

    Optional<UnitOfMeasureJpaEntity> findByCode(String code);

    boolean existsByCode(String code);
}
