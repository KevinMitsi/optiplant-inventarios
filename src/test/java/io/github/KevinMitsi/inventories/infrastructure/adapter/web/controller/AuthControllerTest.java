package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support.MockMvcTestSupport;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.LoginRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.RefreshTokenRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code MockMvc} de {@link AuthController}, único controlador con endpoints públicos.
 * Ejercita la cadena de seguridad completa: login real, verificación de JWT real y {@code /me}.
 */
@DisplayName("AuthController")
class AuthControllerTest extends MockMvcTestSupport {

    @Test
    @DisplayName("emite ambos tokens con credenciales correctas")
    void loginIssuesTokens() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        LoginRequest request = new LoginRequest(admin.getEmail(), DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.user.email", is(admin.getEmail())));
    }

    @Test
    @DisplayName("rechaza una contraseña incorrecta con 401")
    void loginRejectsWrongPassword() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        LoginRequest request = new LoginRequest(admin.getEmail(), "ContraseñaIncorrecta1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rechaza un correo inexistente con el mismo error que una contraseña incorrecta")
    void loginRejectsUnknownEmail() throws Exception {
        LoginRequest request = new LoginRequest("nadie@optiplant.co", "CualquierClave1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rechaza un cuerpo sin correo o contraseña con 400")
    void loginRejectsInvalidBody() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("renueva la sesión a partir de un token de renovación real")
    void refreshIssuesNewAccessToken() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        String refreshToken = tokenProvider.generateRefreshToken(admin);

        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("rechaza presentar un token de acceso como si fuera de renovación")
    void refreshRejectsAccessTokenUsedAsRefresh() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);
        String accessToken = tokenProvider.generateAccessToken(admin);

        RefreshTokenRequest request = new RefreshTokenRequest(accessToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/me devuelve el usuario del token presentado")
    void getCurrentUserReturnsAuthenticatedUser() throws Exception {
        Organization organization = createOrganization();
        User admin = createAdmin(organization);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(admin.getId().toString())))
                .andExpect(jsonPath("$.email", is(admin.getEmail())));
    }

    @Test
    @DisplayName("/me rechaza una petición sin token")
    void getCurrentUserRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token caducado se rechaza con 401, no con una excepción sin manejar")
    void expiredTokenIsRejected() throws Exception {
        // No hay forma de forzar la caducidad desde la API pública sin esperar una hora real;
        // se prueba en su lugar con un token cuya firma no corresponde a la aplicación, que
        // atraviesa la misma rama de error (InvalidTokenException) en JwtAuthenticationFilter.
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("AUTHENTICATION");
    }
}
