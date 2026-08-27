package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.InvalidCredentialsException;
import io.github.KevinMitsi.inventories.application.exception.InvalidTokenException;
import io.github.KevinMitsi.inventories.application.port.in.AuthenticateUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.AuthenticationCommand;
import io.github.KevinMitsi.inventories.application.port.in.result.AuthenticationResult;
import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import io.github.KevinMitsi.inventories.application.port.out.TokenClaims;
import io.github.KevinMitsi.inventories.application.port.out.TokenProviderPort;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Acceso al sistema y renovación de sesión (HU-01, RF-01).
 *
 * <p>Las tres causas por las que puede fallar un intento de acceso —correo inexistente,
 * contraseña incorrecta, cuenta dada de baja— producen la <b>misma</b> excepción y el mismo
 * mensaje. Distinguirlas convertiría el formulario en un medio de averiguar qué direcciones
 * están registradas. El motivo real sí queda en el log del servidor, donde sirve para
 * diagnosticar sin quedar expuesto.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class AuthenticationService implements AuthenticateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    /**
     * Hash de referencia contra el que se verifica cuando el correo no existe.
     *
     * <p>Sin esto, un correo desconocido respondería en microsegundos —no hay hash que
     * comprobar— mientras que uno registrado tardaría lo que tarda BCrypt, que es
     * deliberadamente lento. Esa diferencia de tiempo, medida a escala, permite enumerar
     * qué direcciones existen sin necesidad de acertar ni una contraseña. Verificar contra
     * un hash cualquiera iguala ambos caminos.
     *
     * <p>Es un hash público y conocido; no protege nada y no es un secreto.
     */
    private static final String TIMING_SAFE_DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenProviderPort tokenProvider;

    public AuthenticationService(UserRepositoryPort userRepository,
                                 PasswordHasherPort passwordHasher,
                                 TokenProviderPort tokenProvider) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public AuthenticationResult authenticate(AuthenticationCommand command) {
        String email = normalizeEmail(command.email());

        Optional<User> candidate = email == null
                ? Optional.empty()
                : userRepository.findByEmail(email);

        if (candidate.isEmpty()) {
            // Se consume el mismo tiempo que si el usuario existiera. El resultado se
            // descarta: solo interesa el coste de calcularlo.
            passwordHasher.matches(
                    command.password() == null ? "" : command.password(),
                    TIMING_SAFE_DUMMY_HASH);

            log.warn("Intento de acceso con un correo no registrado: {}", email);
            throw new InvalidCredentialsException();
        }

        User user = candidate.get();

        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            log.warn("Contraseña incorrecta para el usuario {}", user.getId());
            throw new InvalidCredentialsException();
        }

        // Se comprueba después de la contraseña, y no antes, para que el tiempo de respuesta
        // de una cuenta desactivada no la delate frente a una activa.
        if (!user.canAuthenticate()) {
            log.warn("Intento de acceso de una cuenta desactivada: {}", user.getId());
            throw new InvalidCredentialsException();
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);

        log.info("Acceso correcto: usuario={}, rol={}, sucursal={}",
                user.getId(), user.getRoleCode(), user.getBranchId());

        return issueTokens(user);
    }

    @Override
    public AuthenticationResult refresh(String refreshToken) {
        TokenClaims claims = tokenProvider.parseAndValidate(refreshToken);

        // Un token de acceso no sirve para renovar. Sin esta comprobación, el token de vida
        // corta valdría como si fuera de vida larga y la distinción entre ambos se perdería.
        if (claims.isAccessToken()) {
            throw new InvalidTokenException(InvalidTokenException.Reason.WRONG_TYPE);
        }

        // Se recarga el usuario en lugar de confiar en lo que afirma el token. Es lo que
        // hace efectiva una baja o un cambio de rol: el token sigue siendo válido
        // criptográficamente, pero el estado real manda.
        User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> {
                    log.warn("Token de renovación de un usuario inexistente: {}", claims.userId());
                    return new InvalidTokenException(InvalidTokenException.Reason.INVALID);
                });

        if (!user.canAuthenticate()) {
            log.warn("Token de renovación de una cuenta desactivada: {}", user.getId());
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID);
        }

        log.debug("Sesión renovada para el usuario {}", user.getId());
        return issueTokens(user);
    }

    private AuthenticationResult issueTokens(User user) {
        return new AuthenticationResult(
                tokenProvider.generateAccessToken(user),
                tokenProvider.generateRefreshToken(user),
                tokenProvider.getAccessTokenTtl(),
                user);
    }

    /** Normaliza igual que el agregado {@code User}, para que la búsqueda encuentre la cuenta. */
    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
