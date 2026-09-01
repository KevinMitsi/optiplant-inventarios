package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateCategoryCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateCategoryCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.CategorySearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.CategoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.exception.BusinessRuleViolationException;
import io.github.KevinMitsi.inventories.domain.model.Category;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CategoryUseCase")
class CategoryServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Mock
    private CategoryRepositoryPort categoryRepository;
    @Mock
    private OrganizationRepositoryPort organizationRepository;

    @InjectMocks
    private CategoryUseCase service;

    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.reconstitute(CATEGORY_ID, ORGANIZATION_ID, "BEB", "Bebidas",
                "Bebidas frías", true, Instant.now(), Instant.now());

        when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(categoryRepository.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    @DisplayName("Alta")
    class Creation {

        @Test
        @DisplayName("crea la categoría cuando el código está libre")
        void createsCategory() {
            // Arrange
            when(categoryRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "BEB"))
                    .thenReturn(false);

            // Act
            Category created = service.createCategory(
                    new CreateCategoryCommand(ORGANIZATION_ID, "BEB", "Bebidas", null));

            // Assert
            assertThat(created.getCode()).isEqualTo("BEB");
            assertThat(created.isActive()).isTrue();
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("normaliza el código antes de comprobar duplicados")
        void normalizesCodeBeforeDuplicateCheck() {
            // Arrange
            when(categoryRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "BEB"))
                    .thenReturn(false);

            // Act
            service.createCategory(new CreateCategoryCommand(ORGANIZATION_ID, " beb ", "Bebidas", null));

            // Assert
            verify(categoryRepository).existsByOrganizationIdAndCode(ORGANIZATION_ID, "BEB");
        }

        @Test
        @DisplayName("falla si el código ya está en uso y no guarda nada")
        void failsOnDuplicateCode() {
            // Arrange
            when(categoryRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "BEB"))
                    .thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.createCategory(
                    new CreateCategoryCommand(ORGANIZATION_ID, "BEB", "Bebidas", null)))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("falla si la organización no existe, sin consultar categorías")
        void failsWhenOrganizationMissing() {
            // Arrange
            when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> service.createCategory(
                    new CreateCategoryCommand(ORGANIZATION_ID, "BEB", "Bebidas", null)))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(categoryRepository);
        }
    }

    @Nested
    @DisplayName("Baja lógica")
    class Deactivation {

        @Test
        @DisplayName("da de baja la categoría cuando no tiene productos activos")
        void deactivatesWhenNoActiveProducts() {
            // Arrange
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
            when(categoryRepository.countActiveProducts(CATEGORY_ID)).thenReturn(0L);

            // Act
            Category result = service.deactivateCategory(CATEGORY_ID);

            // Assert
            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("rechaza dar de baja una categoría con productos activos")
        void rejectsDeactivationWithActiveProducts() {
            // Arrange
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
            when(categoryRepository.countActiveProducts(CATEGORY_ID)).thenReturn(7L);

            // Act & Assert
            assertThatThrownBy(() -> service.deactivateCategory(CATEGORY_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("7 producto(s) activo(s)");
            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("dar de baja una categoría ya inactiva no consulta productos")
        void skipsProductCheckWhenAlreadyInactive() {
            // Arrange
            category.deactivate();
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));

            // Act
            service.deactivateCategory(CATEGORY_ID);

            // Assert
            verify(categoryRepository, never()).countActiveProducts(any());
        }

        @Test
        @DisplayName("reactivar no comprueba productos")
        void activationSkipsProductCheck() {
            // Arrange
            category.deactivate();
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));

            // Act
            Category result = service.activateCategory(CATEGORY_ID);

            // Assert
            assertThat(result.isActive()).isTrue();
            verify(categoryRepository, never()).countActiveProducts(any());
        }
    }

    @Nested
    @DisplayName("Modificación y consulta")
    class Rest {

        @Test
        @DisplayName("actualizar no altera el código")
        void updateKeepsCode() {
            // Arrange
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));

            // Act
            Category result = service.updateCategory(
                    new UpdateCategoryCommand(CATEGORY_ID, "Bebidas y refrescos", "Nueva"));

            // Assert
            assertThat(result.getName()).isEqualTo("Bebidas y refrescos");
            assertThat(result.getCode()).isEqualTo("BEB");
        }

        @Test
        @DisplayName("falla si la categoría no existe")
        void failsWhenCategoryMissing() {
            // Arrange
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.getCategoryById(CATEGORY_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("delega la búsqueda paginada en el puerto")
        void delegatesSearch() {
            // Arrange
            PageQuery pageQuery = PageQuery.of(0, 20);
            CategorySearchCriteria criteria = CategorySearchCriteria.ofOrganization(ORGANIZATION_ID);
            when(categoryRepository.search(criteria, pageQuery))
                    .thenReturn(new PageResult<>(List.of(category), 0, 20, 1L));

            // Act
            PageResult<Category> result = service.searchCategories(criteria, pageQuery);

            // Assert
            assertThat(result.content()).containsExactly(category);
            assertThat(result.totalElements()).isEqualTo(1L);
        }
    }
}
