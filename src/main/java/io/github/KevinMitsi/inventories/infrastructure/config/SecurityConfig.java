package io.github.KevinMitsi.inventories.infrastructure.config;

import io.github.KevinMitsi.inventories.infrastructure.adapter.security.JwtAuthenticationFilter;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.SecurityErrorResponder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


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

    /** Orígenes del frontend admitidos para CORS, inyectados por variable de entorno. */
    @Value("${app.cors.allowed-origins}")
    private List<String> corsAllowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponder.writeUnauthorized(request, response))
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponder.writeForbidden(request, response)))

                // Cabeceras de defensa en profundidad para la respuesta.
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable)
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable));

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

    /**
     * Orígenes permitidos vienen de {@code app.cors.allowed-origins} (env var
     * {@code APP_CORS_ALLOWED_ORIGINS}, lista separada por comas) para poder variar el
     * dominio del frontend por entorno sin tocar código.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
