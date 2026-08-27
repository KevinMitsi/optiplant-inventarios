package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.BranchSearchCriteria;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.BranchJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.BranchPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.BranchPersistenceMapperImpl;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.BranchJpaRepository;
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
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BranchPersistenceAdapter")
class BranchPersistenceAdapterTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private BranchJpaRepository repository;

    private BranchPersistenceAdapter adapter;
    private BranchPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BranchPersistenceMapperImpl();
        adapter = new BranchPersistenceAdapter(repository, mapper);
    }

    private Branch sampleBranch() {
        return Branch.create(ORGANIZATION_ID, "BOG-01", "Sucursal Chapinero",
                "Calle 63 #11-24", "Bogotá", "CO", "+57 601 5551234");
    }

    @Test
    @DisplayName("guarda traduciendo a entidad y devuelve el modelo de dominio")
    void savesThroughMapper() {
        // Arrange
        Branch branch = sampleBranch();
        when(repository.save(any(BranchJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        // Act
        Branch saved = adapter.save(branch);

        // Assert
        ArgumentCaptor<BranchJpaEntity> captor = ArgumentCaptor.forClass(BranchJpaEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("BOG-01");
        assertThat(captor.getValue().getOrganizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(saved.getCode()).isEqualTo("BOG-01");
    }

    @Test
    @DisplayName("traduce la búsqueda por código a modelo de dominio")
    void findsByCode() {
        // Arrange
        BranchJpaEntity entity = mapper.toEntity(sampleBranch());
        when(repository.findByOrganizationIdAndCode(ORGANIZATION_ID, "BOG-01"))
                .thenReturn(Optional.of(entity));

        // Act
        Optional<Branch> result = adapter.findByOrganizationIdAndCode(ORGANIZATION_ID, "BOG-01");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getName()).isEqualTo("Sucursal Chapinero");
    }

    @Test
    @DisplayName("traduce el resultado paginado conservando los metadatos")
    void translatesPagedResult() {
        // Arrange
        BranchJpaEntity entity = mapper.toEntity(sampleBranch());
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1L));

        // Act
        PageResult<Branch> result = adapter.search(
                BranchSearchCriteria.ofOrganization(ORGANIZATION_ID), PageQuery.of(0, 20));

        // Assert
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("aplica el orden por defecto cuando no se solicita ninguno")
    void appliesDefaultSort() {
        // Arrange
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        // Act
        adapter.search(BranchSearchCriteria.ofOrganization(ORGANIZATION_ID), PageQuery.of(0, 20));

        // Assert
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("code")).isNotNull();
    }

    @Test
    @DisplayName("rechaza ordenar por un campo fuera de la lista blanca")
    void rejectsUnlistedSortField() {
        // Arrange
        PageQuery query = PageQuery.of(0, 20, "organizationId", SortDirection.ASC);

        // Act & Assert
        assertThatThrownBy(() -> adapter.search(
                BranchSearchCriteria.ofOrganization(ORGANIZATION_ID), query))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("No se puede ordenar por");
    }

    @Test
    @DisplayName("delega el conteo de sucursales activas en el repositorio")
    void delegatesActiveCount() {
        // Arrange
        when(repository.countActiveByOrganizationId(ORGANIZATION_ID)).thenReturn(4L);

        // Act & Assert
        assertThat(adapter.countActiveByOrganizationId(ORGANIZATION_ID)).isEqualTo(4L);
    }

    @Test
    @DisplayName("devuelve vacío cuando la sucursal no existe")
    void returnsEmptyWhenMissing() {
        // Arrange
        when(repository.findById(BRANCH_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThat(adapter.findById(BRANCH_ID)).isEmpty();
    }
}
