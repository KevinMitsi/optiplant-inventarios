package io.github.KevinMitsi.inventories.infrastructure.config;

import io.github.KevinMitsi.inventories.infrastructure.adapter.security.JwtAuthenticationFilter;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.SecurityErrorResponder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de seguridad HTTP (EP-01, RF-01, RF-02, RNF-03).
 *
 * <p><b>Todo está cerrado salvo lo que se abre explícitamente.</b> La regla final es
 * {@code anyRequest().authenticated()}, de modo que un endpoint nuevo nace protegido. La
 * alternativa —abrir por omisión y cerrar lo sensible— convierte cada olvido en una fuga.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    /** Rutas abiertas: obtener un token y consultar la documentación. */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorResponder errorResponder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Sin protección CSRF porque no hay cookies de sesión que el navegador
                // adjunte por su cuenta. El token viaja en una cabecera que un sitio
                // externo no puede añadir a una petición forjada, así que el vector que
                // CSRF protege no existe aquí.
                .csrf(csrf -> csrf.disable())

                // Sin estado en servidor: cada petición se autentica por sí sola. Es lo que
                // permite añadir instancias sin compartir sesiones entre ellas (RNF-08).
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // CORS envía una petición previa sin cabecera Authorization; si no
                        // se admite, el navegador nunca llega a enviar la petición real.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())

                // Se sitúa antes del filtro de usuario y contraseña porque este sistema no
                // autentica con formulario: cuando la cadena llegue a ese punto, la
                // identidad ya debe estar establecida a partir del token.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Sin esto, un fallo de autenticación devolvería la página de error del
                // contenedor en HTML, con una forma distinta al resto de errores de la API.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponder.writeUnauthorized(request, response))
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponder.writeForbidden(request, response)))

                // Cabeceras de defensa en profundidad para la respuesta.
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(options -> {
                        })
                        .cacheControl(cache -> {
                        }));

        return http.build();
    }

    /**
     * Algoritmo de cifrado de contraseñas.
     *
     * <p>BCrypt incorpora la sal dentro del propio hash y tiene un coste configurable que
     * puede elevarse a medida que el hardware mejora. Las contraseñas nunca se almacenan en
     * claro ni se escriben en los registros (RNF-03).
     *
     * <p>Se deja el coste por omisión (10). Subirlo encarece cada intento de acceso tanto
     * para el usuario legítimo como para quien pruebe contraseñas por fuerza bruta, así que
     * es una palanca a considerar si el perfil de riesgo lo justifica.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
