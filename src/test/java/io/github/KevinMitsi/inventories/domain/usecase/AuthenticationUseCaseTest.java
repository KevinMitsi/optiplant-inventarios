package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.InvalidCredentialsException;
import io.github.KevinMitsi.inventories.application.exception.InvalidTokenException;
import io.github.KevinMitsi.inventories.application.port.in.command.AuthenticationCommand;
import io.github.KevinMitsi.inventories.application.port.in.result.AuthenticationResult;
import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import io.github.KevinMitsi.inventories.application.port.out.TokenClaims;
import io.github.KevinMitsi.inventories.application.port.out.TokenProviderPort;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.Role;
import io.github.KevinMitsi.inventories.domain.model.RoleCode;
import io.github.KevinMitsi.inventories.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link AuthenticationUseCase}.
 *
 * <p>Buena parte de ellas comprueba <em>qué no se revela</em>: que las tres formas de fallar
 * un acceso produzcan la misma respuesta, y que un correo desconocido consuma el mismo
 * trabajo que uno registrado. Son propiedades de seguridad que no se ven en la respuesta
 * correcta, y por eso conviene fijarlas con pruebas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthenticationUseCase")
class AuthenticationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final String EMAIL = "ana.torres@optiplant.co";
    private static final String RAW_PASSWORD = "MiClaveSegura2026";
    private static final String PASSWORD_HASH = "$2a$10$hashAlmacenado";

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private TokenProviderPort tokenProvider;

    @InjectMocks
    private AuthenticationUseCase service;

    private User activeUser;

    @BeforeEach
    void setUp() {
        Role managerRole = new Role(UUID.randomUUID(), RoleCode.BRANCH_MANAGER,
                "Gerente de sucursal", null);

        activeUser = User.reconstitute(USER_ID, ORGANIZATION_ID, BRANCH_ID, managerRole,
                "Ana", "Torres", EMAIL, PASSWORD_HASH,
                true, null, Instant.now(), Instant.now());

        when(tokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(tokenProvider.getAccessTokenTtl()).thenReturn(Duration.ofHours(1));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    @DisplayName("Acceso")
    class Login {

        @Test
        @DisplayName("emite ambos tokens cuando las credenciales son correctas")
        void authenticatesSuccessfully() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser));
            when(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);

            AuthenticationResult result =
                    service.authenticate(new AuthenticationCommand(EMAIL, RAW_PASSWORD));

            assertThat(result.accessToken()).isEqualTo("access-token");
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
            assertThat(result.accessTokenTtl()).isEqualTo(Duration.ofHours(1));
            assertThat(result.user()).isEqualTo(activeUser);
        }

        @Test
        @DisplayName("normaliza el correo antes de buscar la cuenta")
        void normalizesEmailBeforeLookup() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser));
            when(passwordHasher.matches(anyString(), anyString())).thenReturn(true);

            service.authenticate(new AuthenticationCommand("  Ana.Torres@OptiPlant.CO ", RAW_PASSWORD));

            // El agregado guarda el correo en minúsculas; buscar sin normalizar no
            // encontraría la cuenta y el acceso fallaría con credenciales correctas.
            verify(userRepository).findByEmail(EMAIL);
        }

        @Test
        @DisplayName("registra el acceso correcto")
        void recordsSuccessfulLogin() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser));
            when(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);

            service.authenticate(new AuthenticationCommand(EMAIL, RAW_PASSWORD));

            assertThat(activeUser.getLastLoginAt()).isNotNull();
            verify(userRepository).save(activeUser);
        }

        @Test
        @DisplayName("un correo no registrado falla sin emitir tokens")
        void unknownEmailFails() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.authenticate(
                    new AuthenticationCommand(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(tokenProvider, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("un correo no registrado consume igualmente una verificación de hash")
        void unknownEmailStillVerifiesAHash() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.authenticate(
                    new AuthenticationCommand(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);

            // Sin esto, un correo desconocido respondería en microsegundos y uno registrado
            // tardaría lo que tarda BCrypt. Esa diferencia, medida a escala, permite
            // enumerar qué direcciones existen sin acertar ni una contraseña.
            verify(passwordHasher).matches(eq(RAW_PASSWORD), anyString());
        }

        @Test
        @DisplayName("una contraseña incorrecta falla sin emitir tokens")
        void wrongPasswordFails() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser));
            when(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> service.authenticate(
                    new AuthenticationCommand(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(tokenProvider, never()).generateAccessToken(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("una cuenta dada de baja falla aunque la contraseña sea correcta")
        void inactiveAccountFails() {
            activeUser.deactivate();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser));
            when(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);

            assertThatThrownBy(() -> service.authenticate(
                    new AuthenticationCommand(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(tokenProvider, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("las tres causas de fallo producen el mismo mensaje")
        void allFailuresLookIdentical() {
            // Distinguirlas convertiría el formulario en un medio de averiguar qué
            // direcciones están registradas y cuáles siguen activas.
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            String unknownEmailMessage = messageOf(EMAIL, RAW_PASSWORD);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(activeUser));
            when(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);
            String wrongPasswordMessage = messageOf(EMAIL, RAW_PASSWORD);

            activeUser.deactivate();
            when(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
            String inactiveAccountMessage = messageOf(EMAIL, RAW_PASSWORD);

            assertThat(unknownEmailMessage)
                    .isEqualTo(wrongPasswordMessage)
                    .isEqualTo(inactiveAccountMessage);
        }

        private String messageOf(String email, String password) {
            try {
                service.authenticate(new AuthenticationCommand(email, password));
                throw new AssertionError("Se esperaba que el acceso fallara.");
            } catch (InvalidCredentialsException exception) {
                return exception.getMessage();
            }
        }
    }

    @Nested
    @DisplayName("Renovación")
    class Refresh {

        private TokenClaims refreshClaims(boolean isRefresh) {
            return new TokenClaims(USER_ID, ORGANIZATION_ID, BRANCH_ID, RoleCode.BRANCH_MANAGER,
                    EMAIL, Instant.now(), Instant.now().plus(Duration.ofDays(7)), isRefresh);
        }

        @Test
        @DisplayName("emite tokens nuevos a partir de un token de renovación válido")
        void refreshesSuccessfully() {
            when(tokenProvider.parseAndValidate("refresh")).thenReturn(refreshClaims(true));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

            AuthenticationResult result = service.refresh("refresh");

            assertThat(result.accessToken()).isEqualTo("access-token");
            assertThat(result.user()).isEqualTo(activeUser);
        }

        @Test
        @DisplayName("recarga el usuario en lugar de fiarse de lo que afirma el token")
        void reloadsUserFromRepository() {
            when(tokenProvider.parseAndValidate("refresh")).thenReturn(refreshClaims(true));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

            service.refresh("refresh");

            // Es lo que hace efectiva una baja o un cambio de rol: el token sigue siendo
            // válido criptográficamente, pero el estado real de la cuenta manda.
            verify(userRepository).findById(USER_ID);
        }

        @Test
        @DisplayName("rechaza un token de acceso presentado como si fuera de renovación")
        void rejectsAccessTokenUsedAsRefresh() {
            when(tokenProvider.parseAndValidate("access")).thenReturn(refreshClaims(false));

            assertThatThrownBy(() -> service.refresh("access"))
                    .isInstanceOf(InvalidTokenException.class);

            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("rechaza el token de renovación de una cuenta dada de baja")
        void rejectsRefreshOfDeactivatedAccount() {
            activeUser.deactivate();
            when(tokenProvider.parseAndValidate("refresh")).thenReturn(refreshClaims(true));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> service.refresh("refresh"))
                    .isInstanceOf(InvalidTokenException.class);

            verify(tokenProvider, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("rechaza el token de renovación de un usuario que ya no existe")
        void rejectsRefreshOfMissingUser() {
            when(tokenProvider.parseAndValidate("refresh")).thenReturn(refreshClaims(true));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refresh("refresh"))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}
