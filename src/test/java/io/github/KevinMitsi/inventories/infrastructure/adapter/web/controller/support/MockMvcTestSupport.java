package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller.support;

import io.github.KevinMitsi.inventories.TestcontainersConfiguration;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import io.github.KevinMitsi.inventories.application.port.out.RoleRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TokenProviderPort;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.Organization;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Base para pruebas {@code MockMvc} que ejercitan la cadena de seguridad completa.
 *
 * <p>Levanta el contexto real de Spring (incluido {@code SecurityFilterChain},
 * {@code JwtAuthenticationFilter} y {@code JwtTokenProviderAdapter}) sobre Postgres real vía
 * Testcontainers, y emite tokens con {@link TokenProviderPort} — nunca {@code @WithMockUser},
 * que no atraviesa el filtro JWT ni prueba nada de la emisión/verificación real de tokens.
 *
 * <p>Cada prueba corre en su propia transacción, revertida al final: los datos que sembraron
 * los métodos {@code create*} no se filtran de una prueba a otra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class MockMvcTestSupport {

    /** Contraseña en claro usada por los métodos {@code create*}, para probar el login real. */
    protected static final String DEFAULT_PASSWORD = "Password123!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TokenProviderPort tokenProvider;

    @Autowired
    protected PasswordHasherPort passwordHasher;

    @Autowired
    protected OrganizationRepositoryPort organizationRepository;

    @Autowired
    protected BranchRepositoryPort branchRepository;

    @Autowired
    protected RoleRepositoryPort roleRepository;

    @Autowired
    protected UserRepositoryPort userRepository;

    protected Organization createOrganization() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Organization organization = Organization.create(
                "ORG-" + suffix, "Organización de prueba " + suffix, null, null);
        return organizationRepository.save(organization);
    }

    protected Branch createBranch(Organization organization) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Branch branch = Branch.create(organization.getId(), "BR-" + suffix,
                "Sucursal de prueba " + suffix, "Calle falsa 123", "Bogotá", "CO", "+57 3000000000");
        return branchRepository.save(branch);
    }

    /** Crea un usuario activo con contraseña conocida y el rol indicado. */
    protected User createUser(Organization organization, Branch branch, RoleCode roleCode, String rawPassword) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol %s no está en el catálogo de referencia.".formatted(roleCode)));

        UUID branchId = roleCode == RoleCode.ADMIN ? null : branch.getId();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = User.create(organization.getId(), branchId, role,
                "Nombre", "Prueba", "user-" + suffix + "@test.optiplant.co",
                passwordHasher.hash(rawPassword));

        return userRepository.save(user);
    }

    protected User createAdmin(Organization organization) {
        return createUser(organization, null, RoleCode.ADMIN, DEFAULT_PASSWORD);
    }

    protected User createBranchManager(Organization organization, Branch branch) {
        return createUser(organization, branch, RoleCode.BRANCH_MANAGER, DEFAULT_PASSWORD);
    }

    protected User createInventoryOperator(Organization organization, Branch branch) {
        return createUser(organization, branch, RoleCode.INVENTORY_OPERATOR, DEFAULT_PASSWORD);
    }

    /** Cabecera {@code Authorization} lista para usar en una petición MockMvc. */
    protected String bearer(User user) {
        return "Bearer " + tokenProvider.generateAccessToken(user);
    }
}
