package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PageQueryTranslator")
class PageQueryTranslatorTest {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt");
    private static final String DEFAULT_SORT = "code";

    @Nested
    @DisplayName("Conversión a Pageable")
    class ToPageable {

        @Test
        @DisplayName("aplica el orden por defecto cuando no se pide ninguno")
        void appliesDefaultSort() {
            // Arrange
            PageQuery query = PageQuery.of(2, 15);

            // Act
            Pageable pageable = PageQueryTranslator.toPageable(query, SORTABLE, DEFAULT_SORT);

            // Assert
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(15);
            assertThat(pageable.getSort().getOrderFor(DEFAULT_SORT)).isNotNull();
            assertThat(pageable.getSort().getOrderFor(DEFAULT_SORT).isAscending()).isTrue();
        }

        @Test
        @DisplayName("respeta el campo y el sentido solicitados")
        void appliesRequestedSort() {
            // Arrange
            PageQuery query = PageQuery.of(0, 20, "name", SortDirection.DESC);

            // Act
            Pageable pageable = PageQueryTranslator.toPageable(query, SORTABLE, DEFAULT_SORT);

            // Assert
            Sort.Order order = pageable.getSort().getOrderFor("name");
            assertThat(order).isNotNull();
            assertThat(order.isDescending()).isTrue();
        }

        @Test
        @DisplayName("rechaza ordenar por un campo fuera de la lista blanca")
        void rejectsFieldOutsideWhitelist() {
            // Arrange
            PageQuery query = PageQuery.of(0, 20, "passwordHash", SortDirection.ASC);

            // Act & Assert
            assertThatThrownBy(() -> PageQueryTranslator.toPageable(query, SORTABLE, DEFAULT_SORT))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("No se puede ordenar por");
        }

        @Test
        @DisplayName("el mensaje de rechazo enumera los campos admitidos")
        void rejectionListsAllowedFields() {
            // Arrange
            PageQuery query = PageQuery.of(0, 20, "inexistente", SortDirection.ASC);

            // Act & Assert
            assertThatThrownBy(() -> PageQueryTranslator.toPageable(query, SORTABLE, DEFAULT_SORT))
                    .hasMessageContaining("code")
                    .hasMessageContaining("name")
                    .hasMessageContaining("createdAt");
        }
    }

    @Nested
    @DisplayName("Conversión a PageResult")
    class ToPageResult {

        @Test
        @DisplayName("traslada contenido y metadatos aplicando el mapeo")
        void mapsContentAndMetadata() {
            // Arrange
            Page<String> page = new PageImpl<>(
                    List.of("uno", "dos"), PageRequest.of(1, 2), 7L);

            // Act
            PageResult<Integer> result = PageQueryTranslator.toPageResult(page, String::length);

            // Assert
            assertThat(result.content()).containsExactly(3, 3);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(7L);
            assertThat(result.totalPages()).isEqualTo(4);
            assertThat(result.isFirst()).isFalse();
            assertThat(result.isLast()).isFalse();
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("una página vacía conserva los metadatos coherentes")
        void handlesEmptyPage() {
            // Arrange
            Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L);

            // Act
            PageResult<String> result = PageQueryTranslator.toPageResult(page, value -> value);

            // Assert
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.totalElements()).isZero();
            assertThat(result.totalPages()).isZero();
            assertThat(result.hasNext()).isFalse();
        }
    }
}
