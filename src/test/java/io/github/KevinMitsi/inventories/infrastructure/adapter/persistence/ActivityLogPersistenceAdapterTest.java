package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.ActivityLogSearchCriteria;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.ActivityLogLevel;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ActivityLogJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.ActivityLogPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.ActivityLogPersistenceMapperImpl;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.ActivityLogJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityLogPersistenceAdapter")
class ActivityLogPersistenceAdapterTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Mock
    private ActivityLogJpaRepository repository;

    private ActivityLogPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        ActivityLogPersistenceMapper mapper = new ActivityLogPersistenceMapperImpl();
        adapter = new ActivityLogPersistenceAdapter(repository, mapper);
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    private void stubEmptyPage() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<ActivityLogJpaEntity>(
                        List.of(), invocation.getArgument(1), 0));
    }

    @Test
    @DisplayName("guarda la entrada y la devuelve traducida a dominio")
    void savesAndMapsBack() {
        ActivityLog entry = ActivityLog.of(Instant.parse("2026-09-02T10:15:30Z"), "admin@test.co",
                UUID.randomUUID(), ORGANIZATION_ID, "ADMIN", "CategoryUseCase",
                "Categoría creada", ActivityLogLevel.INFO);

        when(repository.save(any(ActivityLogJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ActivityLog saved = adapter.save(entry);

        assertThat(saved).isEqualTo(entry);
    }

    @Test
    @DisplayName("sin ordenación explícita devuelve lo más reciente primero")
    void defaultsToMostRecentFirst() {
        stubEmptyPage();

        PageResult<ActivityLog> result = adapter.search(
                ActivityLogSearchCriteria.ofOrganization(ORGANIZATION_ID), PageQuery.of(0, 20));

        assertThat(capturePageable().getSort()).isEqualTo(Sort.by("occurredAt").descending());
        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("respeta la ordenación pedida cuando es un campo admitido")
    void honoursRequestedSort() {
        stubEmptyPage();

        adapter.search(ActivityLogSearchCriteria.ofOrganization(ORGANIZATION_ID),
                PageQuery.of(1, 10, "username", SortDirection.ASC));

        Pageable pageable = capturePageable();
        assertThat(pageable).isEqualTo(PageRequest.of(1, 10, Sort.by("username").ascending()));
    }

    @Test
    @DisplayName("rechaza ordenar por un campo que no está en la lista blanca")
    void rejectsUnknownSortField() {
        assertThatThrownBy(() -> adapter.search(
                ActivityLogSearchCriteria.ofOrganization(ORGANIZATION_ID),
                PageQuery.of(0, 20, "operation; DROP TABLE activity_log", SortDirection.ASC)))
                .isInstanceOf(DomainValidationException.class);
    }
}
