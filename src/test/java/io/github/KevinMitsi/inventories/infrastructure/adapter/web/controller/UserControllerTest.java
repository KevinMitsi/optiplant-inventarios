package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support.MockMvcTestSupport;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ChangePasswordRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CreateUserRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ReassignUserRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.UpdateUserProfileRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code MockMvc} de {@link UserController}, con JWT real. Cubre los dos niveles de
 * autorización del controlador: {@code @PreAuthorize} por rol y {@code requireSelfOrAdmin} /
 * {@code requireSelfOrManager}, que dependen del recurso y no se pueden anotar.
 */
@DisplayName("UserController")
class UserControllerTest extends MockMvcTestSupport {

    @Test
    @DisplayName("ADMIN da de alta un operador de inventario en su sucursal")
    void adminCreatesUser() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);

        CreateUserRequest request = new CreateUserRequest(branch.getId(), RoleCode.INVENTORY_OPERATOR,
                "Ana", "Torres", "ana.torres@optiplant.co", "MiClaveSegura2026");

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/users", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("ana.torres@optiplant.co")));
    }

    @Test
    @DisplayName("un gerente de sucursal no puede crear usuarios")
    void branchManagerCannotCreateUser() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User manager = createBranchManager(organization, branch);

        CreateUserRequest request = new CreateUserRequest(branch.getId(), RoleCode.INVENTORY_OPERATOR,
                "Ana", "Torres", "ana.torres@optiplant.co", "MiClaveSegura2026");

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/users", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rechaza crear usuarios en una organización ajena aunque el rol sea ADMIN")
    void rejectsCreateUserForForeignOrganization() throws Exception {
        Organization ownOrganization = createOrganization();
        Organization otherOrganization = createOrganization();
        Branch otherBranch = createBranch(otherOrganization);
        User admin = createAdmin(ownOrganization);

        CreateUserRequest request = new CreateUserRequest(otherBranch.getId(), RoleCode.INVENTORY_OPERATOR,
                "Ana", "Torres", "ana.torres@optiplant.co", "MiClaveSegura2026");

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/users", otherOrganization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un operador de inventario no puede listar usuarios")
    void inventoryOperatorCannotSearchUsers() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/users", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un usuario puede consultarse a sí mismo aunque sea operador de inventario")
    void userCanGetSelf() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);

        mockMvc.perform(get("/api/v1/users/{userId}", operator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(operator.getId().toString())));
    }

    @Test
    @DisplayName("un operador de inventario no puede consultar a otro usuario")
    void inventoryOperatorCannotGetAnotherUser() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);
        User anotherOperator = createInventoryOperator(organization, branch);

        mockMvc.perform(get("/api/v1/users/{userId}", anotherOperator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un gerente de sucursal sí puede consultar a otro usuario")
    void branchManagerCanGetAnotherUser() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User manager = createBranchManager(organization, branch);
        User operator = createInventoryOperator(organization, branch);

        mockMvc.perform(get("/api/v1/users/{userId}", operator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un usuario actualiza su propio perfil")
    void userUpdatesOwnProfile() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);

        UpdateUserProfileRequest request = new UpdateUserProfileRequest("Ana María", "Torres Rojas");

        mockMvc.perform(put("/api/v1/users/{userId}/profile", operator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Ana María")));
    }

    @Test
    @DisplayName("un usuario no puede editar el perfil de otro")
    void userCannotUpdateAnotherProfile() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);
        User anotherOperator = createInventoryOperator(organization, branch);

        UpdateUserProfileRequest request = new UpdateUserProfileRequest("Ana María", "Torres Rojas");

        mockMvc.perform(put("/api/v1/users/{userId}/profile", anotherOperator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN reasigna rol y sucursal")
    void adminReassignsUser() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);
        User operator = createInventoryOperator(organization, branch);

        ReassignUserRequest request = new ReassignUserRequest(RoleCode.BRANCH_MANAGER, branch.getId());

        mockMvc.perform(put("/api/v1/users/{userId}/assignment", operator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("BRANCH_MANAGER")));
    }

    @Test
    @DisplayName("un gerente no puede reasignar usuarios")
    void branchManagerCannotReassignUser() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User manager = createBranchManager(organization, branch);
        User operator = createInventoryOperator(organization, branch);

        ReassignUserRequest request = new ReassignUserRequest(RoleCode.BRANCH_MANAGER, branch.getId());

        mockMvc.perform(put("/api/v1/users/{userId}/assignment", operator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un usuario cambia su propia contraseña con la actual correcta")
    void userChangesOwnPassword() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);

        ChangePasswordRequest request = new ChangePasswordRequest(DEFAULT_PASSWORD, "NuevaClaveSegura2026");

        mockMvc.perform(post("/api/v1/users/{userId}/password", operator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("rechaza cambiar la contraseña de otro usuario, incluso siendo ADMIN")
    void adminCannotChangeAnotherUsersPassword() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);
        User operator = createInventoryOperator(organization, branch);

        ChangePasswordRequest request = new ChangePasswordRequest(DEFAULT_PASSWORD, "NuevaClaveSegura2026");

        mockMvc.perform(post("/api/v1/users/{userId}/password", operator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN desactiva y reactiva una cuenta")
    void adminDeactivatesAndReactivatesUser() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);
        User operator = createInventoryOperator(organization, branch);

        mockMvc.perform(post("/api/v1/users/{userId}/deactivation", operator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(post("/api/v1/users/{userId}/activation", operator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("un operador no puede dar de baja usuarios")
    void inventoryOperatorCannotDeactivateUser() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);
        User anotherOperator = createInventoryOperator(organization, branch);

        mockMvc.perform(post("/api/v1/users/{userId}/deactivation", anotherOperator.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rechaza sin token")
    void rejectsWithoutToken() throws Exception {
        Organization organization = createOrganization();

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/users", organization.getId()))
                .andExpect(status().isUnauthorized());
    }
}
