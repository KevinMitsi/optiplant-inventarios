package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adaptador que satisface {@link PasswordHasherPort} con BCrypt.
 *
 * <p>Es la única clase del proyecto que conoce el algoritmo. Cambiarlo por Argon2 sería
 * sustituir el {@code PasswordEncoder} inyectado, sin tocar un solo caso de uso.
 *
 * <p>BCrypt incorpora una sal aleatoria dentro del propio hash, de modo que dos usuarios con
 * la misma contraseña producen hashes distintos. Por eso verificar consiste en pedírselo al
 * algoritmo y no en volver a cifrar y comparar cadenas, que nunca coincidirían.
 */
@Component
@RequiredArgsConstructor
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        // Ante entradas nulas se responde que no coinciden, en lugar de propagar un fallo.
        // Un error aquí distinguiría el caso "no hay contraseña" del caso "no coincide",
        // que es exactamente la diferencia que la autenticación se esfuerza en ocultar.
        if (rawPassword == null || passwordHash == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
