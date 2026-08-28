package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code MockMvc} de {@link CategoryController}, con JWT real. Cubre tanto
 * {@code @PreAuthorize} por rol como {@code requireBelongsToOrganization} por organización.
 */
@DisplayName("CategoryController")
class CategoryControllerTest extends MockMvcTestSupport {

    private CategoryDtos.CreateCategoryRequest createRequest(String code) {
        return new CategoryDtos.CreateCategoryRequest(code, "Bebidas", "Bebidas frías y calientes");
    }

    @Test
    @DisplayName("ADMIN crea una categoría")
    void adminCreatesCategory() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/categories", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("BEB")));
    }

    @Test
    @DisplayName("un operador de inventario no puede crear categorías")
    void inventoryOperatorCannotCreateCategory() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/categories", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rechaza crear en una organización que no es la del usuario")
    void rejectsCreateForForeignOrganization() throws Exception {
        Organization ownOrganization = createOrganization();
        Organization otherOrganization = createOrganization();
        User admin = createAdmin(ownOrganization);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/categories", otherOrganization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lista categorías de la organización")
    void searchesCategories() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/categories", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/categories", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code", is("BEB")));
    }

    @Test
    @DisplayName("cualquier rol autenticado puede consultar una categoría por id")
    void anyRoleCanGetCategoryById() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User admin = createAdmin(organization);
        User operator = createInventoryOperator(organization, branch);

        String body = mockMvc.perform(post("/api/v1/organizations/{organizationId}/categories", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        CategoryDtos.CategoryResponse created =
                objectMapper.readValue(body, CategoryDtos.CategoryResponse.class);

        mockMvc.perform(get("/api/v1/categories/{categoryId}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("BEB")));
    }

    @Test
    @DisplayName("responde 404 cuando la categoría no existe")
    void returns404WhenCategoryMissing() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/categories/{categoryId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("desactiva y reactiva una categoría")
    void deactivatesAndReactivatesCategory() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        String body = mockMvc.perform(post("/api/v1/organizations/{organizationId}/categories", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        CategoryDtos.CategoryResponse created =
                objectMapper.readValue(body, CategoryDtos.CategoryResponse.class);

        mockMvc.perform(post("/api/v1/categories/{categoryId}/deactivation", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(post("/api/v1/categories/{categoryId}/activation", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("actualiza nombre y descripción")
    void updatesCategory() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        String body = mockMvc.perform(post("/api/v1/organizations/{organizationId}/categories", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        CategoryDtos.CategoryResponse created =
                objectMapper.readValue(body, CategoryDtos.CategoryResponse.class);

        CategoryDtos.UpdateCategoryRequest update =
                new CategoryDtos.UpdateCategoryRequest("Bebidas y refrescos", "Actualizada");

        mockMvc.perform(put("/api/v1/categories/{categoryId}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Bebidas y refrescos")));
    }

    @Test
    @DisplayName("rechaza sin token")
    void rejectsWithoutToken() throws Exception {
        Organization organization = createOrganization();

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/categories", organization.getId()))
                .andExpect(status().isUnauthorized());
    }
}
