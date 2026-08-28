package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support.MockMvcTestSupport;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.LogisticsRouteDtos;
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

/** Pruebas {@code MockMvc} de {@link LogisticsRouteController}, con JWT real. */
@DisplayName("LogisticsRouteController")
class LogisticsRouteControllerTest extends MockMvcTestSupport {

    private LogisticsRouteDtos.CreateLogisticsRouteRequest createRequest(Branch origin, Branch destination) {
        return new LogisticsRouteDtos.CreateLogisticsRouteRequest(
                origin.getId(), destination.getId(), "Bogotá-Medellín", 480,
                new java.math.BigDecimal("250000"), (short) 1);
    }

    private LogisticsRouteDtos.LogisticsRouteResponse createRoute(
            Organization organization, User user, Branch origin, Branch destination) throws Exception {

        String body = mockMvc.perform(post("/api/v1/organizations/{organizationId}/logistics-routes", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(origin, destination))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(body, LogisticsRouteDtos.LogisticsRouteResponse.class);
    }

    @Test
    @DisplayName("un gerente registra una ruta logística")
    void branchManagerCreatesRoute() throws Exception {
        Organization organization = createOrganization();
        Branch origin = createBranch(organization);
        Branch destination = createBranch(organization);
        User manager = createBranchManager(organization, origin);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/logistics-routes", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(origin, destination))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Bogotá-Medellín")));
    }

    @Test
    @DisplayName("un operador de inventario no puede crear rutas logísticas")
    void inventoryOperatorCannotCreateRoute() throws Exception {
        Organization organization = createOrganization();
        Branch origin = createBranch(organization);
        Branch destination = createBranch(organization);
        User operator = createInventoryOperator(organization, origin);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/logistics-routes", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(origin, destination))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lista rutas logísticas de la organización")
    void searchesRoutes() throws Exception {
        Organization organization = createOrganization();
        Branch origin = createBranch(organization);
        Branch destination = createBranch(organization);
        User admin = createAdmin(organization);
        createRoute(organization, admin, origin, destination);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/logistics-routes", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name", is("Bogotá-Medellín")));
    }

    @Test
    @DisplayName("consulta el cumplimiento de rutas sin transferencias, sin fallar")
    void getsRouteComplianceWithNoTransfers() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/logistics-routes/compliance", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("consulta una ruta logística por identificador")
    void getsRouteById() throws Exception {
        Organization organization = createOrganization();
        Branch origin = createBranch(organization);
        Branch destination = createBranch(organization);
        User admin = createAdmin(organization);
        LogisticsRouteDtos.LogisticsRouteResponse created = createRoute(organization, admin, origin, destination);

        mockMvc.perform(get("/api/v1/logistics-routes/{routeId}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Bogotá-Medellín")));
    }

    @Test
    @DisplayName("responde 404 con un identificador inexistente")
    void returns404WhenMissing() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/logistics-routes/{routeId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("actualiza una ruta logística")
    void updatesRoute() throws Exception {
        Organization organization = createOrganization();
        Branch origin = createBranch(organization);
        Branch destination = createBranch(organization);
        User admin = createAdmin(organization);
        LogisticsRouteDtos.LogisticsRouteResponse created = createRoute(organization, admin, origin, destination);

        LogisticsRouteDtos.UpdateLogisticsRouteRequest update = new LogisticsRouteDtos.UpdateLogisticsRouteRequest(
                "Ruta actualizada", 500, new java.math.BigDecimal("300000"), (short) 2);

        mockMvc.perform(put("/api/v1/logistics-routes/{routeId}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Ruta actualizada")));
    }

    @Test
    @DisplayName("desactiva y reactiva una ruta logística")
    void deactivatesAndReactivatesRoute() throws Exception {
        Organization organization = createOrganization();
        Branch origin = createBranch(organization);
        Branch destination = createBranch(organization);
        User admin = createAdmin(organization);
        LogisticsRouteDtos.LogisticsRouteResponse created = createRoute(organization, admin, origin, destination);

        mockMvc.perform(post("/api/v1/logistics-routes/{routeId}/deactivation", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(post("/api/v1/logistics-routes/{routeId}/activation", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("rechaza sin token")
    void rejectsWithoutToken() throws Exception {
        Organization organization = createOrganization();

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/logistics-routes", organization.getId()))
                .andExpect(status().isUnauthorized());
    }
}
