# Fase 5.7 — Deuda técnica (cierre)

> Estado a 2026-08-28. Cierra "Fase 7 — Deuda técnica" listada en
> `docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V6.md`. Ver ese archivo para el plan completo
> de la Fase 5 top-level.

## Alcance

Tests `MockMvc` con JWT real (no `@WithMockUser`) para los controladores sin cobertura, tests
dedicados de la cadena de seguridad, y verificación final con `./gradlew build` completo.

## Hallazgo previo al trabajo planeado: tres bugs de arranque nunca detectados

Ningún test había levantado antes un contexto Spring completo contra Postgres real:
`InventoriesApplicationTests.contextLoads` llevaba fases enteras fallando "por falta de
Docker local", y ese fue siempre el único diagnóstico registrado. Al correr esta fase con
Docker disponible, `contextLoads` **seguía fallando**, pero por motivos reales, no por Docker.
Los tres se corrigieron porque bloqueaban cualquier test `MockMvc` nuevo:

1. **`BranchJpaEntity.countryCode` no pasaba la validación de esquema de Hibernate.**
   `V1__baseline_schema.sql` declara la columna `CHAR(2)`; Hibernate 6+ ignora el
   `columnDefinition` literal para la validación y la mapea a `VARCHAR` salvo que se declare
   explícitamente. Fix: `@JdbcTypeCode(SqlTypes.CHAR)` en el campo.

2. **Migración a Spring Boot 4.1 / Jackson 3 incompleta.** El proyecto ya estaba en Boot
   4.1.1, pero:
   - `spring-boot-starter-test` en Boot 4 ya no arrastra el slice MockMvc (movido a un
     módulo propio por tecnología web). Faltaba `spring-boot-starter-webmvc-test`, y el
     paquete de `@AutoConfigureMockMvc` cambió a
     `org.springframework.boot.webmvc.test.autoconfigure`.
   - Boot 4 autoconfigura por defecto `tools.jackson.databind.ObjectMapper` (Jackson 3), no
     `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2) — no hay bean del segundo tipo
     sin añadir un starter de compatibilidad aparte. `SecurityErrorResponder` importaba el
     tipo antiguo. Fix: cambiar a `tools.jackson.databind.ObjectMapper` (API equivalente,
     `JacksonException` ahora es unchecked).
   - `spring.jackson.serialization.write-dates-as-timestamps` ya no existe en el enum
     `SerializationFeature` de Jackson 3: la propiedad se movió a
     `spring.jackson.datatype.datetime.write-dates-as-timestamps` (`DateTimeFeature`).
     `application.yaml` seguía con la ruta vieja, y el binder fallaba el arranque completo.

3. **Ambigüedad de bean sistémica entre cada `@Service` y su `@Bean` crudo en
   `UseCaseConfig`.** Los 18 `domain.usecase.*` implementan directamente las mismas
   interfaces `port.in` que su `application.service.*Service` envolvente, así que cualquier
   consumidor que dependiera de la interfaz (todo controlador) encontraba dos candidatos y
   Spring fallaba con `NoUniqueBeanDefinitionException` en el primer arranque real. La
   arquitectura documentada en `CLAUDE.md` ya decía "`UseCaseConfig` no expone beans
   `port.in`" — la intención estaba clara, pero faltaba forzarla. Fix: `@Primary` en los 18
   `@Service` (`AuthenticationService`, `BranchService`, `CarrierService`, `CategoryService`,
   `DashboardService`, `InventoryAdjustmentService`, `InventoryAlertService`,
   `InventoryService`, `LogisticsRouteService`, `PriceListService`, `ProductService`,
   `PurchaseOrderService`, `SaleService`, `SupplierService`, `TransferIssueService`,
   `TransferService`, `UnitOfMeasureService`, `UserService`).

Los tres bugs habrían impedido arrancar la aplicación en producción, no solo correr tests.
Quedan documentados aquí porque ninguno estaba en el plan original de Fase 7 — surgieron de
poner a prueba, por primera vez, que el contexto real cargara.

## `DataBootstrapper`: pendiente descartado, no implementado

`PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V6.md` pedía un test dedicado para
`DataBootstrapper`. La clase **no existe en el código** — `CLAUDE.md` es explícito: "No
bootstrap/auto-seed admin — the app has no automatic first-admin provisioning". Fue diseñada
en `docs/PHASE3-SEGURIDAD.md` (DEC-16) pero, a juzgar por el estado actual del repo, se
descartó después sin actualizar ese documento ni el plan de Fase 5. Decisión con el usuario:
omitir el ítem en lugar de implementar una clase fuera del alcance de esta fase. Si se
retoma en el futuro, `DEC-16` en `PHASE3-SEGURIDAD.md` tiene el diseño.

## Infraestructura de pruebas nueva

- `TestcontainersConfiguration` pasó a `public` (antes package-private): la necesitan los
  tests `MockMvc` bajo `infrastructure.adapter.web.controller`, fuera del paquete raíz.
- `infrastructure.adapter.web.controller.support.MockMvcTestSupport` — clase base
  `@SpringBootTest(webEnvironment = MOCK) @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)
  @Transactional`, con helpers `createOrganization()`, `createBranch(organization)`,
  `createAdmin/createBranchManager/createInventoryOperator(...)` (contraseña conocida vía
  `DEFAULT_PASSWORD`) y `bearer(user)` (emite un JWT real con `TokenProviderPort` — nunca
  `@WithMockUser`, que no atraviesa `JwtAuthenticationFilter` ni prueba nada de la emisión o
  verificación real de tokens). Cada test corre en su propia transacción revertida al final.
- `build.gradle`: añadida `testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'`.

## Tests `MockMvc` añadidos (JWT real, 9 controladores)

`AuthControllerTest`, `BranchControllerTest`, `CategoryControllerTest`,
`ProductControllerTest`, `UnitOfMeasureControllerTest`, `UserControllerTest`,
`DashboardControllerTest`, `CarrierControllerTest`, `LogisticsRouteControllerTest`.

Cada uno cubre, según aplique al controlador: la operación exitosa con el rol adecuado,
`@PreAuthorize` rechazando un rol sin permiso, `requireBelongsToOrganization`/
`requireCanOperateOnBranch`/`requireSelfOrAdmin`/`requireSelfOrManager` rechazando el alcance
equivocado (los casos que una anotación no puede resolver, porque dependen del recurso), 401
sin token, 404 con identificador inexistente, y 400 con cuerpo inválido donde aplica.
`UserControllerTest` es el más extenso: cubre los dos niveles de autorización del controlador
(rol vía `@PreAuthorize` + alcance vía `CurrentUserProvider`) en alta, consulta, perfil,
reasignación, cambio de contraseña y estado de cuenta.

Controladores de Fases 1-5 (`InventoryController`, `PurchaseOrderController`,
`SaleController`, `TransferController`, etc.) quedan fuera de este alcance: la Fase 7 los
listaba como "resto de Fase 3, 4, 5, 6" ya cubierto en cierres previos vía tests de
`domain.usecase`/`application.service`; no se les añadió `MockMvc` porque no formaban parte de
los "6 controladores existentes sin cobertura" que el plan enumeraba explícitamente, ni de los
3 nuevos de Fase 6.

## Tests dedicados de la cadena de seguridad

- `JwtAuthenticationFilterTest` (`infrastructure.adapter.security`, patrón unitario con
  Mockito + `MockHttpServletRequest/Response`/`MockFilterChain` de `spring-test`, sin
  contexto): sin cabecera continúa anónimo; token de acceso válido autentica y continúa;
  token de renovación presentado como de acceso se rechaza con 401 sin continuar la cadena;
  token inválido o caducado se rechaza con 401; cabecera sin prefijo `Bearer` o con el
  prefijo vacío se ignora.
- `SecurityErrorResponderTest`: formato de la respuesta en los tres métodos públicos
  (`writeError`, `writeUnauthorized`, `writeForbidden`), y que no reescribe si la respuesta
  ya estaba comprometida (`response.isCommitted()`).

## Verificado

`./gradlew test`: **440 tests, 440 en verde** (subía de 359 antes de esta fase; incluye por
primera vez `InventoriesApplicationTests.contextLoads` pasando de verdad, no solo "sin fallar
por Docker"). `./gradlew build`: limpio, incluye `asciidoctor` (`NO-SOURCE`: no hay `.adoc`
fuente en el repo todavía ni tests que generen snippets con `document(...)` — la dependencia
Spring REST Docs está preparada pero autoría de la documentación queda fuera de este alcance).

## Pendiente

- `DataBootstrapper`: descartado, ver arriba.
- Autoría de `.adoc` + tests que generen snippets Spring REST Docs (`document(...)`), si se
  quiere `asciidoctor` produciendo documentación real en vez de `NO-SOURCE`.
- `MockMvc` para los controladores de Fases 1-5 no listados en el plan original de Fase 7,
  si se decide ampliar cobertura más allá de lo pedido.

Con esto se cierra el plan de Fase 5 completo (Fases 1-7). Siguiente snapshot:
`docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V7.md`.
