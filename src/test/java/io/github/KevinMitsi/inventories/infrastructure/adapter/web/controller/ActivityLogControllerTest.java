package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import com.jayway.jsonpath.JsonPath;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support.MockMvcTestSupport;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CategoryDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code MockMvc} de {@link ActivityLogController}.
 *
 * <p>La prueba que de verdad importa aquí es {@code categoryCreationLeavesATrace}: ninguna
 * línea de {@code CategoryUseCase} habla de auditoría, y aun así crear una categoría deja
 * su entrada con el usuario y el rol de quien la creó. Eso es lo que prueba que la
 * anotación y el manejador funcionan de punta a punta.
 */
@DisplayName("ActivityLogController")
class ActivityLogControllerTest extends MockMvcTestSupport {

    private void createCategory(Organization organization, User author, String code) throws Exception {
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/categories", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryDtos.CreateCategoryRequest(
                                code, "Bebidas", "Bebidas frías y calientes"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("crear una categoría deja rastro en la traza, sin código de auditoría en el caso de uso")
    void categoryCreationLeavesATrace() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        createCategory(organization, admin, "BEB");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/activity-logs", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("useCase", "CategoryUseCase")
                        .param("text", "categoría creada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].username", is(admin.getEmail())))
                .andExpect(jsonPath("$.content[0].role", is("ADMIN")))
                .andExpect(jsonPath("$.content[0].useCase", is("CategoryUseCase")))
                .andExpect(jsonPath("$.content[0].level", is("INFO")))
                .andExpect(jsonPath("$.content[0].systemGenerated", is(false)))
                .andExpect(jsonPath("$.content[0].organizationId", is(organization.getId().toString())));
    }

    @Test
    @DisplayName("la traza registra el rol con el que se actuó")
    void recordsTheActingRole() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);
        User manager = createBranchManager(organization, branch);

        createCategory(organization, manager, "LAC");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/activity-logs", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("username", manager.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].role", is("BRANCH_MANAGER")));
    }

    @Test
    @DisplayName("consulta una entrada concreta por identificador")
    void getsSingleEntry() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        createCategory(organization, admin, "PAN");

        String body = mockMvc.perform(
                        get("/api/v1/organizations/{organizationId}/activity-logs", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.content[0].id");

        mockMvc.perform(get("/api/v1/activity-logs/{activityLogId}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)));
    }

    @Test
    @DisplayName("un gerente de sucursal no puede leer la traza")
    void branchManagerCannotReadTheTrace() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User manager = createBranchManager(organization, branch);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/activity-logs", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un operador de inventario no puede leer la traza")
    void inventoryOperatorCannotReadTheTrace() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/activity-logs", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rechaza leer la traza de una organización ajena")
    void rejectsForeignOrganization() throws Exception {
        Organization ownOrganization = createOrganization();
        Organization otherOrganization = createOrganization();
        User admin = createAdmin(ownOrganization);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/activity-logs", otherOrganization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("exige autenticación")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/activity-logs", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rechaza un rango de fechas invertido")
    void rejectsInvertedDateRange() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/activity-logs", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("from", "2026-09-02T10:00:00Z")
                        .param("to", "2026-09-01T10:00:00Z"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("rechaza un tamaño de página por encima del máximo")
    void rejectsOversizedPage() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/activity-logs", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("size", "500"))
                .andExpect(status().isBadRequest());
    }
}
