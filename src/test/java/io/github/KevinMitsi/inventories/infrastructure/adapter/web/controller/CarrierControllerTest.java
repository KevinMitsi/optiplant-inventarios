package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support.MockMvcTestSupport;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CarrierDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pruebas {@code MockMvc} de {@link CarrierController}, con JWT real. */
@DisplayName("CarrierController")
class CarrierControllerTest extends MockMvcTestSupport {

    private CarrierDtos.CreateCarrierRequest createRequest() {
        return new CarrierDtos.CreateCarrierRequest("TRANS-01", "Transportes Rápidos S.A.S.",
                "+57 601 5551234", "contacto@transportesrapidos.co");
    }

    private CarrierDtos.CarrierResponse createCarrier(Organization organization, User user) throws Exception {
        String body = mockMvc.perform(post("/api/v1/organizations/{organizationId}/carriers", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(body, CarrierDtos.CarrierResponse.class);
    }

    @Test
    @DisplayName("un gerente registra un transportista")
    void branchManagerCreatesCarrier() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User manager = createBranchManager(organization, branch);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/carriers", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("TRANS-01")));
    }

    @Test
    @DisplayName("un operador de inventario no puede crear transportistas")
    void inventoryOperatorCannotCreateCarrier() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/carriers", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lista transportistas de la organización")
    void searchesCarriers() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        createCarrier(organization, admin);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/carriers", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code", is("TRANS-01")));
    }

    @Test
    @DisplayName("consulta un transportista por identificador")
    void getsCarrierById() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        CarrierDtos.CarrierResponse created = createCarrier(organization, admin);

        mockMvc.perform(get("/api/v1/carriers/{carrierId}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("TRANS-01")));
    }

    @Test
    @DisplayName("responde 404 con un identificador inexistente")
    void returns404WhenMissing() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/carriers/{carrierId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("actualiza un transportista")
    void updatesCarrier() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        CarrierDtos.CarrierResponse created = createCarrier(organization, admin);

        CarrierDtos.UpdateCarrierRequest update = new CarrierDtos.UpdateCarrierRequest(
                "Transportes Rápidos Actualizado", "+57 601 5559999", "nuevo@transportesrapidos.co");

        mockMvc.perform(put("/api/v1/carriers/{carrierId}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Transportes Rápidos Actualizado")));
    }

    @Test
    @DisplayName("desactiva y reactiva un transportista")
    void deactivatesAndReactivatesCarrier() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        CarrierDtos.CarrierResponse created = createCarrier(organization, admin);

        mockMvc.perform(post("/api/v1/carriers/{carrierId}/deactivation", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(post("/api/v1/carriers/{carrierId}/activation", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("rechaza sin token")
    void rejectsWithoutToken() throws Exception {
        Organization organization = createOrganization();

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/carriers", organization.getId()))
                .andExpect(status().isUnauthorized());
    }
}
