package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import io.github.KevinMitsi.inventories.application.exception.OperationNotPermittedException;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CurrentUserProvider")
class CurrentUserProviderTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID OTHER_BRANCH_ID = UUID.randomUUID();

    private CurrentUserProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CurrentUserProvider();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(RoleCode role, UUID branchId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                USER_ID, ORGANIZATION_ID, branchId, role, "ana.torres@optiplant.co");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority(role.asAuthority()))));
    }

    @Nested
    @DisplayName("Lectura del contexto")
    class ContextRead {

        @Test
        @DisplayName("devuelve la identidad cuando la petición está autenticada")
        void returnsAuthenticatedUser() {
            // Arrange
            authenticateAs(RoleCode.BRANCH_MANAGER, BRANCH_ID);

            // Act
            AuthenticatedUser user = provider.require();

            // Assert
            assertThat(user.userId()).isEqualTo(USER_ID);
            assertThat(user.role()).isEqualTo(RoleCode.BRANCH_MANAGER);
            assertThat(user.branchId()).isEqualTo(BRANCH_ID);
        }

        @Test
        @DisplayName("devuelve vacío cuando no hay contexto de seguridad")
        void returnsEmptyWhenAnonymous() {
            // Arrange: contexto limpio

            // Act & Assert
            assertThat(provider.find()).isEmpty();
        }

        @Test
        @DisplayName("falla al exigir identidad en una petición anónima")
        void requireFailsWhenAnonymous() {
            // Arrange: contexto limpio

            // Act & Assert
            assertThatThrownBy(() -> provider.require())
                    .isInstanceOf(CurrentUserProvider.NotAuthenticatedException.class);
        }

        @Test
        @DisplayName("trata como anónimo un sujeto que no sea AuthenticatedUser")
        void ignoresForeignPrincipalType() {
            // Arrange
            SecurityContextHolder.getContext().setAuthentication(
                    new AnonymousAuthenticationToken("clave", "anonimo",
                            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

            // Act & Assert
            assertThat(provider.find())
                    .as("un sujeto de otro tipo no debe interpretarse como identidad válida")
                    .isEmpty();
        }

        @Test
        @DisplayName("expone el identificador para registrar al responsable de un movimiento")
        void exposesUserId() {
            // Arrange
            authenticateAs(RoleCode.INVENTORY_OPERATOR, BRANCH_ID);

            // Act & Assert
            assertThat(provider.requireUserId()).isEqualTo(USER_ID);
        }
    }

    @Nested
    @DisplayName("Alcance por sucursal (RN-12, RN-13)")
    class BranchScope {

        @Test
        @DisplayName("el administrador puede operar sobre cualquier sucursal")
        void adminOperatesAnywhere() {
            // Arrange
            authenticateAs(RoleCode.ADMIN, null);

            // Act
            AuthenticatedUser user = provider.requireCanOperateOnBranch(OTHER_BRANCH_ID, "operar");

            // Assert
            assertThat(user.isAdmin()).isTrue();
        }

        @Test
        @DisplayName("el gerente puede operar sobre su propia sucursal")
        void managerOperatesOnOwnBranch() {
            // Arrange
            authenticateAs(RoleCode.BRANCH_MANAGER, BRANCH_ID);

            // Act
            AuthenticatedUser user = provider.requireCanOperateOnBranch(BRANCH_ID, "operar");

            // Assert
            assertThat(user.branchId()).isEqualTo(BRANCH_ID);
        }

        @Test
        @DisplayName("el gerente no puede operar sobre una sucursal ajena")
        void managerCannotOperateOnForeignBranch() {
            // Arrange
            authenticateAs(RoleCode.BRANCH_MANAGER, BRANCH_ID);

            // Act & Assert
            assertThatThrownBy(() ->
                    provider.requireCanOperateOnBranch(OTHER_BRANCH_ID, "registrar la venta"))
                    .isInstanceOf(OperationNotPermittedException.class)
                    .hasMessageContaining("sucursal distinta");
        }
    }

    @Nested
    @DisplayName("Alcance por organización")
    class OrganizationScope {

        @Test
        @DisplayName("permite operar dentro de la organización propia")
        void allowsOwnOrganization() {
            // Arrange
            authenticateAs(RoleCode.ADMIN, null);

            // Act
            AuthenticatedUser user =
                    provider.requireBelongsToOrganization(ORGANIZATION_ID, "crear usuarios");

            // Assert
            assertThat(user.organizationId()).isEqualTo(ORGANIZATION_ID);
        }

        @Test
        @DisplayName("rechaza operar sobre otra organización, incluso siendo administrador")
        void rejectsForeignOrganizationEvenForAdmin() {
            // Arrange
            authenticateAs(RoleCode.ADMIN, null);
            UUID foreignOrganizationId = UUID.randomUUID();

            // Act & Assert
            assertThatThrownBy(() ->
                    provider.requireBelongsToOrganization(foreignOrganizationId, "crear usuarios"))
                    .isInstanceOf(OperationNotPermittedException.class)
                    .hasMessageContaining("organización distinta");
        }
    }
}
