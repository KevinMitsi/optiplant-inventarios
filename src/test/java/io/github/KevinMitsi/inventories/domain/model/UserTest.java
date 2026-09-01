package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del modelo de dominio {@link User}.
 *
 * <p>Se concentran en las dos cosas que este agregado protege: la coherencia entre rol y
 * sucursal, y las reglas de alcance RN-12 y RN-13, que son la base de toda la autorización
 * del sistema.
 */
@DisplayName("User (modelo de dominio)")
class UserTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID OTHER_BRANCH_ID = UUID.randomUUID();
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuv";

    private static Role role(RoleCode code) {
        return new Role(UUID.randomUUID(), code, code.name(), null);
    }

    private static User userWith(RoleCode code, UUID branchId) {
        return User.create(ORGANIZATION_ID, branchId, role(code),
                "Ana", "Torres", "ana.torres@optiplant.co", PASSWORD_HASH);
    }

    @Nested
    @DisplayName("Coherencia entre rol y sucursal")
    class RoleBranchConsistency {

        @ParameterizedTest
        @EnumSource(value = RoleCode.class, names = {"BRANCH_MANAGER", "INVENTORY_OPERATOR"})
        @DisplayName("un rol que opera en una sucursal debe tenerla asignada")
        void operationalRoleRequiresBranch(RoleCode code) {
            // Sin sucursal no podría registrar nada: toda escritura pertenece a una
            // sucursal concreta (RN-02).
            assertThatThrownBy(() -> userWith(code, null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("sucursal");
        }

        @Test
        @DisplayName("el administrador general no puede tener sucursal asignada")
        void adminMustNotHaveBranch() {
            // Asignarle una haría ambiguo su alcance: opera sobre todas (RN-12).
            assertThatThrownBy(() -> userWith(RoleCode.ADMIN, BRANCH_ID))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("no puede estar asignado a una sucursal");
        }

        @Test
        @DisplayName("el administrador general se crea sin sucursal")
        void adminWithoutBranchIsValid() {
            User admin = userWith(RoleCode.ADMIN, null);

            assertThat(admin.getBranchId()).isNull();
            assertThat(admin.getRoleCode()).isEqualTo(RoleCode.ADMIN);
        }

        @Test
        @DisplayName("promover a administrador obliga a liberar la sucursal en el mismo cambio")
        void promotingToAdminReleasesBranch() {
            User user = userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID);

            user.reassign(role(RoleCode.ADMIN), null);

            assertThat(user.getRoleCode()).isEqualTo(RoleCode.ADMIN);
            assertThat(user.getBranchId()).isNull();
        }

        @Test
        @DisplayName("promover a administrador conservando la sucursal se rechaza")
        void promotingToAdminKeepingBranchFails() {
            User user = userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID);

            assertThatThrownBy(() -> user.reassign(role(RoleCode.ADMIN), BRANCH_ID))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("degradar a un administrador obliga a asignarle una sucursal")
        void demotingAdminRequiresBranch() {
            User admin = userWith(RoleCode.ADMIN, null);

            assertThatThrownBy(() -> admin.reassign(role(RoleCode.BRANCH_MANAGER), null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("sucursal");
        }
    }

    @Nested
    @DisplayName("Reglas de alcance (RN-12, RN-13)")
    class Scope {

        @Test
        @DisplayName("el administrador general opera sobre cualquier sucursal")
        void adminOperatesOnAnyBranch() {
            User admin = userWith(RoleCode.ADMIN, null);

            assertThat(admin.canOperateOnBranch(BRANCH_ID)).isTrue();
            assertThat(admin.canOperateOnBranch(OTHER_BRANCH_ID)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = RoleCode.class, names = {"BRANCH_MANAGER", "INVENTORY_OPERATOR"})
        @DisplayName("los demás roles solo operan sobre la sucursal asignada")
        void otherRolesOperateOnlyOnTheirBranch(RoleCode code) {
            User user = userWith(code, BRANCH_ID);

            assertThat(user.canOperateOnBranch(BRANCH_ID)).isTrue();
            assertThat(user.canOperateOnBranch(OTHER_BRANCH_ID))
                    .as("RN-13: un gerente opera dentro de su sucursal")
                    .isFalse();
        }

        @Test
        @DisplayName("un usuario dado de baja no opera sobre ninguna sucursal")
        void inactiveUserOperatesNowhere() {
            User user = userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID);
            user.deactivate();

            assertThat(user.canOperateOnBranch(BRANCH_ID)).isFalse();
        }

        @Test
        @DisplayName("un administrador dado de baja tampoco opera, pese a su rol")
        void inactiveAdminOperatesNowhere() {
            User admin = userWith(RoleCode.ADMIN, null);
            admin.deactivate();

            assertThat(admin.canOperateOnBranch(BRANCH_ID))
                    .as("el estado de la cuenta pesa más que el alcance del rol")
                    .isFalse();
        }

        @Test
        @DisplayName("la consulta de otras sucursales está abierta a todos los roles")
        void anyActiveRoleCanViewAnyBranch() {
            User operator = userWith(RoleCode.INVENTORY_OPERATOR, BRANCH_ID);

            // Es lo que permite localizar mercancía en la red antes de pedir una
            // transferencia (HU-06, RF-06). La restricción aplica a la escritura.
            assertThat(operator.canViewBranch(OTHER_BRANCH_ID)).isTrue();
        }

        @Test
        @DisplayName("solo el administrador gestiona la organización")
        void onlyAdminManagesOrganization() {
            assertThat(userWith(RoleCode.ADMIN, null).canManageOrganization()).isTrue();
            assertThat(userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID).canManageOrganization()).isFalse();
            assertThat(userWith(RoleCode.INVENTORY_OPERATOR, BRANCH_ID).canManageOrganization()).isFalse();
        }

        @Test
        @DisplayName("aprobar transferencias exige rol de supervisión y alcance sobre la sucursal origen")
        void approvingTransfersRequiresBothRoleAndScope() {
            User manager = userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID);
            User operator = userWith(RoleCode.INVENTORY_OPERATOR, BRANCH_ID);

            assertThat(manager.canApproveTransferFrom(BRANCH_ID)).isTrue();
            assertThat(manager.canApproveTransferFrom(OTHER_BRANCH_ID))
                    .as("no puede comprometer stock de una sucursal ajena")
                    .isFalse();
            assertThat(operator.canApproveTransferFrom(BRANCH_ID))
                    .as("comprometer stock es supervisión, no ejecución")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Normalización y validación")
    class Normalization {

        @Test
        @DisplayName("el correo se normaliza a minúsculas")
        void emailIsLowercased() {
            User user = User.create(ORGANIZATION_ID, BRANCH_ID, role(RoleCode.BRANCH_MANAGER),
                    "Ana", "Torres", "  Ana.Torres@OptiPlant.CO  ", PASSWORD_HASH);

            // Sin esto, dos escrituras distintas del mismo correo serían cuentas distintas
            // para el índice único y la misma persona para quien intenta entrar.
            assertThat(user.getEmail()).isEqualTo("ana.torres@optiplant.co");
        }

        @ParameterizedTest
        @ValueSource(strings = {"sin-arroba", "@empresa.com", "ana@", "a@b@c.com"})
        @DisplayName("rechaza correos con formato inválido")
        void rejectsMalformedEmail(String email) {
            assertThatThrownBy(() -> User.create(ORGANIZATION_ID, BRANCH_ID,
                    role(RoleCode.BRANCH_MANAGER), "Ana", "Torres", email, PASSWORD_HASH))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("correo");
        }

        @Test
        @DisplayName("rechaza un hash de contraseña vacío")
        void rejectsBlankPasswordHash() {
            assertThatThrownBy(() -> User.create(ORGANIZATION_ID, BRANCH_ID,
                    role(RoleCode.BRANCH_MANAGER), "Ana", "Torres",
                    "ana@optiplant.co", "  "))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("la representación textual nunca incluye el hash de la contraseña")
        void toStringNeverLeaksPasswordHash() {
            User user = userWith(RoleCode.BRANCH_MANAGER, BRANCH_ID);

            // Este texto acaba en logs y en trazas de excepción.
            assertThat(user.toString()).doesNotContain(PASSWORD_HASH);
        }
    }

    @Nested
    @DisplayName("Ciclo de vida de la cuenta")
    class Lifecycle {

        @Test
        @DisplayName("una cuenta nueva nace activa y puede autenticarse")
        void newAccountIsActive() {
            User user = userWith(RoleCode.INVENTORY_OPERATOR, BRANCH_ID);

            assertThat(user.isActive()).isTrue();
            assertThat(user.canAuthenticate()).isTrue();
            assertThat(user.getLastLoginAt()).isNull();
        }

        @Test
        @DisplayName("una cuenta dada de baja no puede autenticarse")
        void deactivatedAccountCannotAuthenticate() {
            User user = userWith(RoleCode.INVENTORY_OPERATOR, BRANCH_ID);

            user.deactivate();

            assertThat(user.canAuthenticate()).isFalse();
        }

        @Test
        @DisplayName("registrar un acceso no altera la marca de modificación de la ficha")
        void loginDoesNotTouchUpdatedAt() {
            User user = userWith(RoleCode.INVENTORY_OPERATOR, BRANCH_ID);
            Instant updatedAtBefore = user.getUpdatedAt();

            user.recordSuccessfulLogin();

            assertThat(user.getLastLoginAt()).isNotNull();
            assertThat(user.getUpdatedAt())
                    .as("iniciar sesión no es editar el usuario; mezclar ambas marcas "
                            + "impediría distinguir una cosa de la otra")
                    .isEqualTo(updatedAtBefore);
        }

        @Test
        @DisplayName("dar de baja y reactivar son idempotentes")
        void statusChangesAreIdempotent() {
            User user = userWith(RoleCode.INVENTORY_OPERATOR, BRANCH_ID);

            assertThatCode(user::activate).doesNotThrowAnyException();
            assertThat(user.isActive()).isTrue();

            user.deactivate();
            Instant afterFirst = user.getUpdatedAt();

            assertThatCode(user::deactivate).doesNotThrowAnyException();
            assertThat(user.getUpdatedAt()).isEqualTo(afterFirst);
        }

        @Test
        @DisplayName("cambiar la contraseña sustituye el hash")
        void changePasswordReplacesHash() {
            User user = userWith(RoleCode.INVENTORY_OPERATOR, BRANCH_ID);
            String newHash = "$2a$10$zyxwvutsrqponmlkjihgfe";

            user.changePassword(newHash);

            assertThat(user.getPasswordHash()).isEqualTo(newHash);
        }
    }
}
