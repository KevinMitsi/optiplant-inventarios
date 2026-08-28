package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateBranchCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateBranchCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.BranchSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

/**
 * Pruebas unitarias de {@link BranchUseCase}.
 *
 * <p>Los puertos de salida se sustituyen por dobles, así que no interviene ni la base de
 * datos ni el contexto de Spring. Eso es posible precisamente porque el servicio depende de
 * interfaces declaradas por la propia capa de aplicación y no de Spring Data: si dependiera
 * del repositorio de JPA, esta clase necesitaría un contenedor y las pruebas pasarían de
 * milisegundos a decenas de segundos.
 *
 * <p>Lo que se comprueba aquí es la orquestación —qué se valida, en qué orden, y qué se deja
 * de hacer cuando algo falla—, no las reglas del agregado, que tienen su propia clase.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BranchUseCase")
class BranchServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private BranchRepositoryPort branchRepository;

    @Mock
    private OrganizationRepositoryPort organizationRepository;

    @InjectMocks
    private BranchUseCase service;

    private Branch existingBranch;

    @BeforeEach
    void setUp() {
        existingBranch = Branch.reconstitute(
                BRANCH_ID, ORGANIZATION_ID, "BOG-01", "Sucursal Chapinero",
                "Calle 63 #11-24", "Bogotá", "CO", "+57 601 5551234",
                true, java.time.Instant.now(), java.time.Instant.now());
    }

    private static CreateBranchCommand createCommand(String code) {
        return new CreateBranchCommand(ORGANIZATION_ID, code, "Sucursal Chapinero",
                "Calle 63 #11-24", "Bogotá", "CO", "+57 601 5551234");
    }

    @Nested
    @DisplayName("Creación")
    class Creation {

        @Test
        @DisplayName("registra la sucursal cuando la organización existe y el código está libre")
        void createsBranch() {
            when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
            when(branchRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "BOG-01"))
                    .thenReturn(false);
            when(branchRepository.save(any(Branch.class))).thenAnswer(call -> call.getArgument(0));

            Branch created = service.createBranch(createCommand("BOG-01"));

            assertThat(created.getCode()).isEqualTo("BOG-01");
            assertThat(created.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(created.isActive()).isTrue();
            verify(branchRepository).save(any(Branch.class));
        }

        @Test
        @DisplayName("normaliza el código antes de comprobar duplicados")
        void normalizesCodeBeforeDuplicateCheck() {
            when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
            when(branchRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "BOG-01"))
                    .thenReturn(false);
            when(branchRepository.save(any(Branch.class))).thenAnswer(call -> call.getArgument(0));

            service.createBranch(createCommand("  bog-01  "));

            // Si la comprobación usara el valor sin normalizar, un alta duplicada la pasaría
            // y moriría después contra el índice único con un error opaco.
            verify(branchRepository).existsByOrganizationIdAndCode(ORGANIZATION_ID, "BOG-01");
        }

        @Test
        @DisplayName("falla si la organización no existe, sin llegar a consultar sucursales")
        void failsWhenOrganizationMissing() {
            when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.createBranch(createCommand("BOG-01")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("organización");

            verifyNoInteractions(branchRepository);
        }

        @Test
        @DisplayName("falla si el código ya está en uso en esa organización y no guarda nada")
        void failsWhenCodeAlreadyExists() {
            when(organizationRepository.existsById(ORGANIZATION_ID)).thenReturn(true);
            when(branchRepository.existsByOrganizationIdAndCode(ORGANIZATION_ID, "BOG-01"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createBranch(createCommand("BOG-01")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("BOG-01");

            verify(branchRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Modificación")
    class Modification {

        @Test
        @DisplayName("aplica los cambios sobre el agregado cargado")
        void updatesBranch() {
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existingBranch));
            when(branchRepository.save(any(Branch.class))).thenAnswer(call -> call.getArgument(0));

            service.updateBranch(new UpdateBranchCommand(BRANCH_ID, "Sucursal Chapinero Norte",
                    "Calle 72 #10-34", "Bogotá", "CO", "+57 601 5559876"));

            ArgumentCaptor<Branch> saved = ArgumentCaptor.forClass(Branch.class);
            verify(branchRepository).save(saved.capture());

            assertThat(saved.getValue().getName()).isEqualTo("Sucursal Chapinero Norte");
            assertThat(saved.getValue().getAddressLine()).isEqualTo("Calle 72 #10-34");
            assertThat(saved.getValue().getCode())
                    .as("el código no es modificable")
                    .isEqualTo("BOG-01");
        }

        @Test
        @DisplayName("falla si la sucursal no existe")
        void failsWhenBranchMissing() {
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateBranch(new UpdateBranchCommand(
                    BRANCH_ID, "Nombre", null, null, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(branchRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Estado de alta")
    class Status {

        @Test
        @DisplayName("dar de baja marca la sucursal como inactiva sin borrarla")
        void deactivates() {
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existingBranch));
            when(branchRepository.save(any(Branch.class))).thenAnswer(call -> call.getArgument(0));

            Branch result = service.deactivateBranch(BRANCH_ID);

            assertThat(result.isActive()).isFalse();
            assertThat(result.getId())
                    .as("la baja es lógica: el registro sigue existiendo")
                    .isEqualTo(BRANCH_ID);
        }

        @Test
        @DisplayName("reactivar devuelve la sucursal a la operación")
        void activates() {
            existingBranch.deactivate();
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existingBranch));
            when(branchRepository.save(any(Branch.class))).thenAnswer(call -> call.getArgument(0));

            Branch result = service.activateBranch(BRANCH_ID);

            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("dar de baja una sucursal inexistente falla")
        void deactivateFailsWhenMissing() {
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deactivateBranch(BRANCH_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Consulta")
    class Query {

        @Test
        @DisplayName("devuelve la sucursal solicitada")
        void findsById() {
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existingBranch));

            assertThat(service.getBranchById(BRANCH_ID)).isEqualTo(existingBranch);
        }

        @Test
        @DisplayName("falla cuando el identificador no corresponde a ninguna sucursal")
        void failsWhenNotFound() {
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getBranchById(BRANCH_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(BRANCH_ID.toString());
        }

        @Test
        @DisplayName("normaliza el código antes de buscar por él")
        void normalizesCodeOnLookup() {
            when(branchRepository.findByOrganizationIdAndCode(ORGANIZATION_ID, "BOG-01"))
                    .thenReturn(Optional.of(existingBranch));

            Branch found = service.getBranchByCode(ORGANIZATION_ID, "  bog-01 ");

            assertThat(found).isEqualTo(existingBranch);
            verify(branchRepository).findByOrganizationIdAndCode(ORGANIZATION_ID, "BOG-01");
        }

        @Test
        @DisplayName("delega la búsqueda paginada en el puerto y conserva los metadatos")
        void delegatesSearch() {
            PageQuery pageQuery = PageQuery.of(0, 20);
            BranchSearchCriteria criteria = BranchSearchCriteria.ofOrganization(ORGANIZATION_ID);
            when(branchRepository.search(criteria, pageQuery))
                    .thenReturn(new PageResult<>(List.of(existingBranch), 0, 20, 1L));

            PageResult<Branch> result = service.searchBranches(criteria, pageQuery);

            assertThat(result.content()).containsExactly(existingBranch);
            assertThat(result.totalElements()).isEqualTo(1L);
            assertThat(result.totalPages()).isEqualTo(1);
            assertThat(result.isFirst()).isTrue();
            assertThat(result.isLast()).isTrue();
        }
    }
}
