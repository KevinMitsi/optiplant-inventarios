package io.github.KevinMitsi.inventories.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentación OpenAPI de la API.
 *
 * <p>La especificación se genera a partir de las anotaciones de los controladores y los
 * DTO, de modo que documentación y código no puedan divergir: si un endpoint cambia de
 * firma, la documentación cambia con él. Aquí solo se define lo global —identidad de la
 * API, servidores y esquema de autenticación—, que no pertenece a ningún controlador
 * concreto.
 *
 * <p>Disponible en {@code /swagger-ui.html} y {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI inventoriesOpenApi() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Entorno local"),
                        new Server().url("/").description("Servidor actual")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerScheme()))
                // Requisito global: los endpoints son privados salvo que se indique lo
                // contrario. Es más seguro que ir marcando uno a uno los protegidos, porque
                // olvidarse de una anotación no deja un recurso documentado como abierto.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private Info apiInfo() {
        return new Info()
                .title("OptiPlant — API de Inventario Multi-Sucursal")
                .version("v1")
                .description("""
                        API REST para la gestión de inventario distribuido entre las sucursales \
                        de una organización.

                        ### Principio rector del dominio

                        **El stock nunca cambia sin un movimiento que lo explique** (RN-04). Toda \
                        variación de existencias —una venta, la recepción de una compra, el \
                        despacho o la recepción de una transferencia, un ajuste— queda respaldada \
                        por un registro en el histórico de movimientos, con responsable, fecha, \
                        cantidad y motivo. Ese histórico es inmutable: un error se corrige con un \
                        movimiento de ajuste nuevo, jamás modificando uno pasado (RNF-12).

                        ### Alcance del stock

                        Las existencias pertenecen a la pareja **(sucursal, producto)**, nunca al \
                        producto por sí solo (RN-02). El catálogo es global a la organización; el \
                        inventario es de cada sucursal.

                        ### Tratamiento de errores

                        Todos los errores comparten el mismo cuerpo de respuesta. El campo `code` \
                        identifica la causa concreta y es el valor sobre el que conviene programar \
                        la reacción del cliente, en lugar del código de estado HTTP, que agrupa \
                        situaciones distintas bajo el mismo número.

                        | Estado | Significado |
                        |--------|-------------|
                        | 400 | La petición está mal formada o incumple una validación de formato. |
                        | 401 | Credenciales ausentes, inválidas o sesión expirada. |
                        | 403 | Autenticado, pero sin permiso sobre ese ámbito. |
                        | 404 | El recurso no existe, o no es visible para quien pregunta. |
                        | 409 | Conflicto con el estado actual: duplicado o modificación concurrente. |
                        | 422 | La petición está bien formada pero incumple una regla de negocio. |
                        """)
                .contact(new Contact()
                        .name("Kevin García")
                        .url("https://github.com/KevinMitsi"))
                .license(new License().name("MIT"));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                        Token JWT obtenido en el endpoint de autenticación.

                        Se envía en la cabecera `Authorization` con el prefijo `Bearer`. \
                        El token transporta el identificador del usuario, su rol y su sucursal, \
                        que es lo que permite aplicar las reglas de alcance: el administrador \
                        general ve toda la organización, mientras que un gerente opera dentro de \
                        su propia sucursal (RN-12, RN-13).""");
    }
}
