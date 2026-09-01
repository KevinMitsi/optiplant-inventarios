package io.github.KevinMitsi.inventories.domain.usecase;

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

import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

public class AuthenticationUseCase implements AuthenticateUserUseCase {

    private static final Logger log = Logger.getLogger(AuthenticationUseCase.class.getName());

    /**
     * Hash de referencia contra el que se verifica cuando el correo no existe, para que
     * ambos caminos tarden lo mismo y no se pueda enumerar cuentas por temporización.
     * Es un hash público y conocido; no protege nada.
     */
    private static final String TIMING_SAFE_DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenProviderPort tokenProvider;

    public AuthenticationUseCase(UserRepositoryPort userRepository,
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
            passwordHasher.matches(
                    command.password() == null ? "" : command.password(),
                    TIMING_SAFE_DUMMY_HASH);

            log.warning(() -> "Intento de acceso con un correo no registrado: %s".formatted(email));
            throw new InvalidCredentialsException();
        }

        User user = candidate.get();

        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            log.warning(() -> "Contraseña incorrecta para el usuario %s".formatted(user.getId()));
            throw new InvalidCredentialsException();
        }

        if (!user.canAuthenticate()) {
            log.warning(() -> "Intento de acceso de una cuenta desactivada: %s".formatted(user.getId()));
            throw new InvalidCredentialsException();
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);

        log.info(() -> "Acceso correcto: usuario=%s, rol=%s, sucursal=%s"
                .formatted(user.getId(), user.getRoleCode(), user.getBranchId()));

        return issueTokens(user);
    }

    @Override
    public AuthenticationResult refresh(String refreshToken) {
        TokenClaims claims = tokenProvider.parseAndValidate(refreshToken);

        if (claims.isAccessToken()) {
            throw new InvalidTokenException(InvalidTokenException.Reason.WRONG_TYPE);
        }

        User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> {
                    log.warning(() -> "Token de renovación de un usuario inexistente: %s".formatted(claims.userId()));
                    return new InvalidTokenException(InvalidTokenException.Reason.INVALID);
                });

        if (!user.canAuthenticate()) {
            log.warning(() -> "Token de renovación de una cuenta desactivada: %s".formatted(user.getId()));
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID);
        }

        log.fine(() -> "Sesión renovada para el usuario %s".formatted(user.getId()));
        return issueTokens(user);
    }

    private AuthenticationResult issueTokens(User user) {
        return new AuthenticationResult(
                tokenProvider.generateAccessToken(user),
                tokenProvider.generateRefreshToken(user),
                tokenProvider.getAccessTokenTtl(),
                user);
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
