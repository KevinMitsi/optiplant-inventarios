package io.github.KevinMitsi.inventories.application.port.out;

/**
 * Puerto de salida para el cifrado y la verificación de contraseñas.
 *
 * <p>Existe para que la capa de aplicación no importe BCrypt ni ninguna otra
 * implementación concreta. El algoritmo de hash es una decisión que envejece: hoy es
 * BCrypt, mañana puede ser Argon2. Detrás de este puerto, ese cambio afecta a una clase de
 * infraestructura y a ningún caso de uso.
 *
 * <p>Ninguna implementación debe registrar en logs la contraseña recibida, ni incluirla en
 * el mensaje de una excepción (RNF-03).
 */
public interface PasswordHasherPort {

    /**
     * Cifra una contraseña en claro.
     *
     * @param rawPassword contraseña tal como la escribió el usuario
     * @return hash apto para persistirse, con la sal incorporada
     */
    String hash(String rawPassword);

    /**
     * Comprueba si una contraseña en claro corresponde a un hash almacenado.
     *
     * <p>La comparación debe hacerse con el algoritmo original, nunca cifrando de nuevo y
     * comparando cadenas: el hash lleva su propia sal, así que dos cifrados de la misma
     * contraseña producen resultados distintos.
     *
     * @return {@code true} si la contraseña corresponde al hash
     */
    boolean matches(String rawPassword, String passwordHash);
}
