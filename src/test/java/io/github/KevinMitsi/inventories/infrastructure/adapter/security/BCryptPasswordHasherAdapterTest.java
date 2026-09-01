package io.github.KevinMitsi.inventories.infrastructure.adapter.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BCryptPasswordHasherAdapter")
class BCryptPasswordHasherAdapterTest {

    private static final String RAW_PASSWORD = "MiClaveSegura2026";

    private BCryptPasswordHasherAdapter hasher;

    @BeforeEach
    void setUp() {
        hasher = new BCryptPasswordHasherAdapter(new BCryptPasswordEncoder());
    }

    @Test
    @DisplayName("el hash no revela la contraseña original")
    void hashDoesNotContainRawPassword() {
        // Arrange & Act
        String hash = hasher.hash(RAW_PASSWORD);

        // Assert
        assertThat(hash).isNotEqualTo(RAW_PASSWORD).doesNotContain(RAW_PASSWORD);
    }

    @Test
    @DisplayName("dos cifrados de la misma contraseña producen hashes distintos")
    void samePasswordProducesDifferentHashes() {
        // Arrange & Act
        String first = hasher.hash(RAW_PASSWORD);
        String second = hasher.hash(RAW_PASSWORD);

        // Assert: BCrypt incorpora una sal aleatoria, de ahí que comparar cadenas no sirva
        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.matches(RAW_PASSWORD, first)).isTrue();
        assertThat(hasher.matches(RAW_PASSWORD, second)).isTrue();
    }

    @Test
    @DisplayName("reconoce la contraseña correcta")
    void matchesCorrectPassword() {
        // Arrange
        String hash = hasher.hash(RAW_PASSWORD);

        // Act & Assert
        assertThat(hasher.matches(RAW_PASSWORD, hash)).isTrue();
    }

    @Test
    @DisplayName("rechaza una contraseña incorrecta")
    void rejectsWrongPassword() {
        // Arrange
        String hash = hasher.hash(RAW_PASSWORD);

        // Act & Assert
        assertThat(hasher.matches("OtraClave2026", hash)).isFalse();
    }

    @Test
    @DisplayName("responde que no coinciden ante entradas nulas, sin lanzar")
    void handlesNullsWithoutThrowing() {
        // Arrange
        String hash = hasher.hash(RAW_PASSWORD);

        // Act & Assert: un fallo aquí distinguiría "no hay contraseña" de "no coincide",
        // que es justo la diferencia que la autenticación se esfuerza en ocultar
        assertThat(hasher.matches(null, hash)).isFalse();
        assertThat(hasher.matches(RAW_PASSWORD, null)).isFalse();
        assertThat(hasher.matches(null, null)).isFalse();
    }
}
