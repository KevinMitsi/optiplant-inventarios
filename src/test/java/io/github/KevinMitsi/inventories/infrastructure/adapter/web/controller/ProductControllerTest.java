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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code MockMvc} de {@link ProductController}, con JWT real. Las unidades usadas son
 * las sembradas por {@code V2__reference_data.sql} (UUID fijos).
 */
@DisplayName("ProductController")
class ProductControllerTest extends MockMvcTestSupport {

    private static final UUID UNIT_ID = UUID.fromString("22222222-0000-4000-8000-000000000001");
    private static final UUID PACK_UNIT_ID = UUID.fromString("22222222-0000-4000-8000-000000000008");

    private ProductDtos.CreateProductRequest createRequest(String sku) {
        return createRequest(sku, List.of());
    }

    private ProductDtos.CreateProductRequest createRequest(
            String sku, List<ProductDtos.ProductVariantRequest> variants) {
        return new ProductDtos.CreateProductRequest(
                sku, "Agua Brisa Botella 1 L", null, null, "Agua sin gas", UNIT_ID, variants);
    }

    private ProductDtos.ProductFamilyResponse createProduct(Organization organization, User admin, String sku)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(sku))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(body, ProductDtos.ProductFamilyResponse.class);
    }

    @Test
    @DisplayName("ADMIN da de alta un producto con su unidad y sin variantes")
    void adminCreatesProduct() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("BEB-BRISA-BOT-1L"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principal.sku", is("BEB-BRISA-BOT-1L")))
                .andExpect(jsonPath("$.principal.unit.code", is("UNIT")))
                // Un principal no lleva padre, y la serialización omite los campos nulos.
                .andExpect(jsonPath("$.principal.parentProductId").doesNotExist())
                .andExpect(jsonPath("$.variants.length()", is(0)));
    }

    @Test
    @DisplayName("las variantes se crean con el producto, cada una con su SKU y su unidad")
    void createsProductWithVariants() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        ProductDtos.CreateProductRequest request = createRequest("BEB-BRISA-BOT-1L", List.of(
                new ProductDtos.ProductVariantRequest("BEB-BRISA-BOL-24", "Agua Brisa Bolsa x 24",
                        null, null, null, PACK_UNIT_ID)));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants.length()", is(1)))
                .andExpect(jsonPath("$.variants[0].sku", is("BEB-BRISA-BOL-24")))
                .andExpect(jsonPath("$.variants[0].unit.code", is("PACK")));
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
                        .content(objectMapper.writeValueAsString(createRequest("BEB-BRISA-BOT-1L"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rechaza un cuerpo sin unidad de medida")
    void rejectsMissingUnit() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        ProductDtos.CreateProductRequest request = new ProductDtos.CreateProductRequest(
                "BEB-BRISA-BOT-1L", "Agua Brisa", null, null, null, null, null);

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
        createProduct(organization, admin, "BEB-BRISA-BOT-1L");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku", is("BEB-BRISA-BOT-1L")));
    }

    @Test
    @DisplayName("scope=PRINCIPALS_ONLY deja fuera las variantes")
    void searchesPrincipalsOnly() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        ProductDtos.ProductFamilyResponse family = createProduct(organization, admin, "BEB-BRISA-BOT-1L");
        addVariant(admin, family.principal().id(), "BEB-BRISA-BOL-24");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/products", organization.getId())
                        .param("scope", "PRINCIPALS_ONLY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].sku", is("BEB-BRISA-BOT-1L")));
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
    @DisplayName("consulta un producto por identificador")
    void getsProductById() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        ProductDtos.ProductFamilyResponse created = createProduct(organization, admin, "BEB-BRISA-BOT-1L");

        mockMvc.perform(get("/api/v1/products/{productId}", created.principal().id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku", is("BEB-BRISA-BOT-1L")))
                .andExpect(jsonPath("$.unit.code", is("UNIT")));
    }

    @Test
    @DisplayName("añade una variante a un producto existente y la lista con él")
    void addsVariant() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        ProductDtos.ProductFamilyResponse created = createProduct(organization, admin, "BEB-BRISA-BOT-1L");
        UUID principalId = created.principal().id();

        addVariant(admin, principalId, "BEB-BRISA-BOL-24");

        mockMvc.perform(get("/api/v1/products/{productId}/variants", principalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].sku", is("BEB-BRISA-BOL-24")))
                .andExpect(jsonPath("$[0].parentProductId", is(principalId.toString())));

        mockMvc.perform(get("/api/v1/products/{productId}/family", principalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal.sku", is("BEB-BRISA-BOT-1L")))
                .andExpect(jsonPath("$.variants.length()", is(1)));
    }

    @Test
    @DisplayName("una variante no admite variantes propias")
    void rejectsNestedVariant() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        ProductDtos.ProductFamilyResponse created = createProduct(organization, admin, "BEB-BRISA-BOT-1L");
        UUID variantId = addVariant(admin, created.principal().id(), "BEB-BRISA-BOL-24").id();

        ProductDtos.ProductVariantRequest request = new ProductDtos.ProductVariantRequest(
                "BEB-BRISA-BOL-48", "Agua Brisa Bolsa x 48", null, null, null, PACK_UNIT_ID);

        mockMvc.perform(post("/api/v1/products/{productId}/variants", variantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("desactiva y reactiva un producto")
    void deactivatesAndReactivatesProduct() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        ProductDtos.ProductFamilyResponse created = createProduct(organization, admin, "BEB-BRISA-BOT-1L");

        mockMvc.perform(patch("/api/v1/products/{productId}/deactivation", created.principal().id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(patch("/api/v1/products/{productId}/activation", created.principal().id())
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

    private ProductDtos.ProductResponse addVariant(User admin, UUID parentProductId, String sku) throws Exception {
        ProductDtos.ProductVariantRequest request = new ProductDtos.ProductVariantRequest(
                sku, "Agua Brisa Bolsa x 24", null, null, null, PACK_UNIT_ID);

        String body = mockMvc.perform(post("/api/v1/products/{productId}/variants", parentProductId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(body, ProductDtos.ProductResponse.class);
    }
}
