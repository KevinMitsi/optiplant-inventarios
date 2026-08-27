package io.github.KevinMitsi.inventories.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad HTTP.
 *
 * <p><b>Estado provisional.</b> Mientras no exista el módulo de autenticación, esta cadena
 * deja pasar todas las peticiones para que los endpoints ya construidos puedan ejercitarse.
 * Sustituirla es trabajo pendiente del módulo de seguridad (EP-01, RF-01 y RF-02), que debe:
 * <ul>
 *   <li>añadir el filtro de validación de JWT antes del filtro de autenticación por usuario
 *       y contraseña;</li>
 *   <li>dejar públicos únicamente el endpoint de autenticación y los de documentación;</li>
 *   <li>exigir autenticación para todo lo demás;</li>
 *   <li>habilitar la autorización por método para poder aplicar las reglas de alcance por
 *       rol y sucursal (RN-12, RN-13).</li>
 * </ul>
 *
 * <p>La aplicación no debe desplegarse fuera de un entorno local con esta configuración:
 * tal como está, cualquiera puede invocar cualquier endpoint sin identificarse.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // La API no usa cookies de sesión, así que no hay vector CSRF que proteger:
                // el token viaja en una cabecera que el navegador no adjunta solo.
                .csrf(csrf -> csrf.disable())

                // Sin estado: cada petición se autentica por sí misma. Es lo que permite
                // escalar horizontalmente sin sesiones compartidas entre instancias (RNF-08).
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // TODO(EP-01): sustituir por authenticated() salvo /api/v1/auth/**,
                //              /v3/api-docs/** y /swagger-ui/**, y añadir el filtro JWT.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    /**
     * Algoritmo de hash de contraseñas.
     *
     * <p>BCrypt incorpora la sal en el propio hash y tiene coste configurable, de modo que
     * puede encarecerse a medida que el hardware mejora. Las contraseñas nunca se guardan
     * en claro ni se registran en los logs (RNF-03).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
