package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support.MockMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code MockMvc} de {@link DashboardController}, con JWT real. Sin ventas ni
 * inventario sembrados, las tres proyecciones deben responder listas vacías, no un error.
 */
@DisplayName("DashboardController")
class DashboardControllerTest extends MockMvcTestSupport {

    @Test
    @DisplayName("cualquier rol de la organización consulta el resumen de ventas")
    void anyRoleGetsSalesSummary() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/dashboard/sales-summary", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").exists());
    }

    @Test
    @DisplayName("consulta la rotación de productos")
    void getsProductRotation() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/dashboard/product-rotation", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN compara sucursales")
    void adminGetsBranchComparison() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/dashboard/branch-comparison", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un gerente de sucursal no puede comparar sucursales (RN-12, solo ADMIN)")
    void branchManagerCannotGetBranchComparison() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User manager = createBranchManager(organization, branch);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/dashboard/branch-comparison", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rechaza consultar el dashboard de una organización ajena")
    void rejectsForeignOrganization() throws Exception {
        Organization ownOrganization = createOrganization();
        Organization otherOrganization = createOrganization();
        User admin = createAdmin(ownOrganization);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/dashboard/sales-summary", otherOrganization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rechaza sin token")
    void rejectsWithoutToken() throws Exception {
        Organization organization = createOrganization();

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/dashboard/sales-summary", organization.getId()))
                .andExpect(status().isUnauthorized());
    }
}
