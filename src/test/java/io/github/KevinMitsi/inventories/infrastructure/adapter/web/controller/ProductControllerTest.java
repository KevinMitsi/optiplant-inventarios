package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support.MockMvcTestSupport;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ProductDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code MockMvc} de {@link ProductController}, con JWT real. La unidad base usada es
 * la sembrada por {@code V2__reference_data.sql} (código {@code UNIT}, UUID fijo).
 */
@DisplayName("ProductController")
class ProductControllerTest extends MockMvcTestSupport {

    private static final UUID UNIT_ID = UUID.fromString("22222222-0000-4000-8000-000000000001");
    private static final UUID BOX_UNIT_ID = UUID.fromString("22222222-0000-4000-8000-000000000007");

    private ProductDtos.CreateProductRequest createRequest(String sku) {
        return new ProductDtos.CreateProductRequest(
                sku, "Agua mineral 600 ml", null, null, "Agua sin gas", UNIT_ID);
    }

    private ProductDtos.ProductResponse createProduct(Organization organization, User admin, String sku)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(sku))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(body, ProductDtos.ProductResponse.class);
    }

    @Test
    @DisplayName("ADMIN da de alta un producto con su unidad base")
    void adminCreatesProduct() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB-AGUA-600"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", is("BEB-AGUA-600")))
                .andExpect(jsonPath("$.units[0].baseUnit", is(true)));
    }

    @Test
    @DisplayName("un operador de inventario no puede dar de alta productos")
    void inventoryOperatorCannotCreateProduct() throws Exception {
        Organization organization = createOrganization();
        Branch branch = createBranch(organization);
        User operator = createInventoryOperator(organization, branch);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB-AGUA-600"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rechaza un cuerpo sin unidad base")
    void rejectsMissingBaseUnit() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        ProductDtos.CreateProductRequest request = new ProductDtos.CreateProductRequest(
                "BEB-AGUA-600", "Agua mineral", null, null, null, null);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("consulta el catálogo paginado")
    void searchesProducts() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        createProduct(organization, admin, "BEB-AGUA-600");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku", is("BEB-AGUA-600")));
    }

    @Test
    @DisplayName("rechaza consultar el catálogo de otra organización")
    void rejectsSearchForForeignOrganization() throws Exception {
        Organization ownOrganization = createOrganization();
        Organization otherOrganization = createOrganization();
        User admin = createAdmin(ownOrganization);

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/products", otherOrganization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("consulta un producto por identificador con sus presentaciones")
    void getsProductById() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        ProductDtos.ProductResponse created = createProduct(organization, admin, "BEB-AGUA-600");

        mockMvc.perform(get("/api/v1/products/{productId}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku", is("BEB-AGUA-600")));
    }

    @Test
    @DisplayName("añade una presentación adicional")
    void addsProductUnit() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        ProductDtos.ProductResponse created = createProduct(organization, admin, "BEB-AGUA-600");

        ProductDtos.AddProductUnitRequest request =
                new ProductDtos.AddProductUnitRequest(BOX_UNIT_ID, new BigDecimal("24"));

        mockMvc.perform(post("/api/v1/products/{productId}/units", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units.length()", is(2)));
    }

    @Test
    @DisplayName("desactiva y reactiva un producto")
    void deactivatesAndReactivatesProduct() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        ProductDtos.ProductResponse created = createProduct(organization, admin, "BEB-AGUA-600");

        mockMvc.perform(post("/api/v1/products/{productId}/deactivation", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(post("/api/v1/products/{productId}/activation", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("rechaza sin token")
    void rejectsWithoutToken() throws Exception {
        Organization organization = createOrganization();

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/products", organization.getId()))
                .andExpect(status().isUnauthorized());
    }
}
