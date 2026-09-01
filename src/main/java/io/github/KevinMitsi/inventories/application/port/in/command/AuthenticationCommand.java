package io.github.KevinMitsi.inventories.application.port.in.command;

/**
 * Credenciales presentadas en el intento de acceso (HU-01).
 *
 * <p>La contraseña viaja en claro únicamente en este objeto y solo durante el trayecto
 * entre el controlador y el servicio de autenticación. A partir de ahí se entrega al puerto
 * que la verifica y no se conserva en ninguna parte.
 *
 * <p>Deliberadamente sin {@code toString}: el generado por omisión imprimiría la contraseña,
 * y basta con que este objeto acabe en el mensaje de una excepción o en una traza de
 * depuración para filtrarla (RNF-03). Se redefine más abajo para que eso no pueda ocurrir.
 */
public record AuthenticationCommand(String email, String password) {

    /** Enmascara la contraseña: este texto puede acabar en un log o en una traza. */
    @Override
    public String toString() {
        return "AuthenticationCommand[email=%s, password=***]".formatted(email);
    }
}
