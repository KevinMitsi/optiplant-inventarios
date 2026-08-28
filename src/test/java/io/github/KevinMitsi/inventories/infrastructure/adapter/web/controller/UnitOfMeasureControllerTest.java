package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support.MockMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code MockMvc} de {@link UnitOfMeasureController}, con JWT real. El catálogo de
 * unidades es de solo lectura y viene sembrado por {@code V2__reference_data.sql} con UUID
 * fijos, así que las pruebas se apoyan en esos identificadores conocidos en lugar de crear datos.
 */
@DisplayName("UnitOfMeasureController")
class UnitOfMeasureControllerTest extends MockMvcTestSupport {

    private static final UUID UNIT_ID = UUID.fromString("22222222-0000-4000-8000-000000000001");

    @Test
    @DisplayName("lista el catálogo completo de unidades")
    void listsAllUnits() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/units-of-measure")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    @Test
    @DisplayName("consulta una unidad por identificador")
    void getsUnitById() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/units-of-measure/{unitId}", UNIT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("UNIT")));
    }

    @Test
    @DisplayName("responde 404 con un identificador inexistente")
    void returns404WhenMissing() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/units-of-measure/{unitId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("rechaza sin token")
    void rejectsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/units-of-measure"))
                .andExpect(status().isUnauthorized());
    }
}
