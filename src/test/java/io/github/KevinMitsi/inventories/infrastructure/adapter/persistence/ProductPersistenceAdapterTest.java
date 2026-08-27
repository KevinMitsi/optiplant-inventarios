package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.in.query.ProductSearchCriteria;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.domain.model.UnitOfMeasure;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.ProductJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.CatalogPersistenceMapper;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper.CatalogPersistenceMapperImpl;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.ProductJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
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

/**
 * Pruebas del adaptador de productos.
 *
 * <p>El repositorio de Spring Data se sustituye por un doble; el mapeador es el real, porque
 * la responsabilidad del adaptador es precisamente traducir y eso es lo que se verifica.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductPersistenceAdapter")
class ProductPersistenceAdapterTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private ProductJpaRepository repository;

    private ProductPersistenceAdapter adapter;
    private CatalogPersistenceMapper mapper;
    private UnitOfMeasure bottle;

    @BeforeEach
    void setUp() {
        mapper = new CatalogPersistenceMapperImpl();
        adapter = new ProductPersistenceAdapter(repository, mapper);
        bottle = UnitOfMeasure.create("UNIT", "Unidad", "und");
    }

    private Product sampleProduct() {
        return Product.create(ORGANIZATION_ID, null, "BEB-AGUA-600", null,
                "Agua mineral 600 ml", null, bottle);
    }

    @Test
    @DisplayName("guarda traduciendo a entidad y devuelve el modelo de dominio")
    void savesThroughMapper() {
        // Arrange
        Product product = sampleProduct();
        when(repository.save(any(ProductJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        // Act
        Product saved = adapter.save(product);

        // Assert
        ArgumentCaptor<ProductJpaEntity> captor = ArgumentCaptor.forClass(ProductJpaEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSku()).isEqualTo("BEB-AGUA-600");
        assertThat(saved.getSku()).isEqualTo("BEB-AGUA-600");
        assertThat(saved.getUnits()).hasSize(1);
    }

    @Test
    @DisplayName("devuelve vacío cuando el producto no existe")
    void returnsEmptyWhenMissing() {
        // Arrange
        when(repository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        // Act
        Optional<Product> result = adapter.findById(PRODUCT_ID);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("traduce el resultado paginado conservando los metadatos")
    void translatesPagedResult() {
        // Arrange
        ProductJpaEntity entity = mapper.toEntity(sampleProduct());
        Page<ProductJpaEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(1, 5), 11L);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // Act
        PageResult<Product> result = adapter.search(
                ProductSearchCriteria.ofOrganization(ORGANIZATION_ID), PageQuery.of(1, 5));

        // Assert
        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(11L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("aplica el orden solicitado cuando el campo está permitido")
    void appliesAllowedSort() {
        // Arrange
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        // Act
        adapter.search(ProductSearchCriteria.ofOrganization(ORGANIZATION_ID),
                PageQuery.of(0, 20, "sku", SortDirection.DESC));

        // Assert
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("sku")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("sku").isDescending()).isTrue();
    }

    @Test
    @DisplayName("rechaza ordenar por un campo fuera de la lista blanca")
    void rejectsUnlistedSortField() {
        // Arrange
        PageQuery query = PageQuery.of(0, 20, "organizationId", SortDirection.ASC);

        // Act & Assert
        assertThatThrownBy(() -> adapter.search(
                ProductSearchCriteria.ofOrganization(ORGANIZATION_ID), query))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("delega las comprobaciones de existencia en el repositorio")
    void delegatesExistenceChecks() {
        // Arrange
        when(repository.existsByOrganizationIdAndSku(ORGANIZATION_ID, "SKU-1")).thenReturn(true);
        when(repository.existsByOrganizationIdAndBarcode(ORGANIZATION_ID, "770")).thenReturn(false);

        // Act & Assert
        assertThat(adapter.existsByOrganizationIdAndSku(ORGANIZATION_ID, "SKU-1")).isTrue();
        assertThat(adapter.existsByOrganizationIdAndBarcode(ORGANIZATION_ID, "770")).isFalse();
    }
}
