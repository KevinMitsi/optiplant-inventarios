package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support.MockMvcTestSupport;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CreateBranchRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.UpdateBranchRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code MockMvc} de {@link BranchController}, con JWT real emitido por
 * {@code TokenProviderPort} — ejercita la cadena de seguridad completa, no un usuario simulado.
 */
@DisplayName("BranchController")
class BranchControllerTest extends MockMvcTestSupport {

    @Test
    @DisplayName("crea una sucursal con un token válido")
    void createsBranchWithValidToken() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        CreateBranchRequest request = new CreateBranchRequest(
                "BOG-01", "Sucursal Chapinero", "Calle 63 #11-24", "Bogotá", "CO", "+57 601 5551234");

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/branches", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("BOG-01")))
                .andExpect(jsonPath("$.organizationId", is(organization.getId().toString())));
    }

    @Test
    @DisplayName("rechaza crear una sucursal sin token")
    void rejectsCreateWithoutToken() throws Exception {
        Organization organization = createOrganization();

        CreateBranchRequest request = new CreateBranchRequest(
                "BOG-01", "Sucursal Chapinero", null, null, null, null);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/branches", organization.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rechaza un token manipulado")
    void rejectsTamperedToken() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        String tampered = bearer(admin) + "tampered";

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/branches", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rechaza un cuerpo inválido con 400")
    void rejectsInvalidBody() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        CreateBranchRequest request = new CreateBranchRequest(
                "", "Sucursal Chapinero", null, null, null, null);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/branches", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lista sucursales de la organización")
    void searchesBranches() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/branches", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code", is(branch.getCode())));
    }

    @Test
    @DisplayName("consulta una sucursal por identificador")
    void getsBranchById() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/branches/{branchId}", branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(branch.getId().toString())));
    }

    @Test
    @DisplayName("responde 404 cuando la sucursal no existe")
    void returns404WhenBranchMissing() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/branches/{branchId}", java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("actualiza una sucursal")
    void updatesBranch() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);

        UpdateBranchRequest request = new UpdateBranchRequest(
                "Nuevo nombre", "Nueva dirección", "Medellín", "CO", "+57 604 5551234");

        mockMvc.perform(put("/api/v1/branches/{branchId}", branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Nuevo nombre")));
    }

    @Test
    @DisplayName("desactiva y reactiva una sucursal")
    void deactivatesAndReactivatesBranch() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);

        mockMvc.perform(patch("/api/v1/branches/{branchId}/deactivation", branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(patch("/api/v1/branches/{branchId}/activation", branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }
}
