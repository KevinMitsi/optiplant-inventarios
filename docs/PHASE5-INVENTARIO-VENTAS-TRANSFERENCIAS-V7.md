# Fase 5 — Inventario, Compras, Ventas, Transferencias, Logística, Dashboard

> Estado a 2026-08-28. Plan aprobado original: `snazzy-snacking-peach.md` (guardado en
> `~/.claude/plans/`). Este documento reemplaza esa referencia efímera con una que vive en
> el repo y sirve para retomar en otra sesión. Ver también `docs/PHASE5.3-VENTAS-CIERRE.md`,
> `docs/PHASE5.4-TRANSFERENCIAS-CIERRE.md`, `docs/PHASE5.5-LOGISTICA-CIERRE.md`,
> `docs/PHASE5.6-DASHBOARD-CIERRE.md` y `docs/PHASE5.7-DEUDA-TECNICA-CIERRE.md` para el
> detalle de cómo se cerraron las Fases 3-7.
>
> **Versionado**: este archivo NO se sobreescribe en el mismo nombre al cerrar una fase — el
> sufijo `-V{n}` sube (`n` = número de la última fase cerrada al momento del snapshot) y el
> archivo anterior se conserva para dejar rastro auditable de la evolución del plan. Este es
> `V7` (snapshot tras cerrar Fase 7 — Deuda técnica). **Con esto el plan de Fase 5 completo
> (Fases 1-7) queda cerrado.** Si se abre trabajo nuevo sobre este módulo, crear
> `PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V8.md` describiendo su alcance, no editar este
> archivo in-place.
>
> **Numeración de archivos**: todo esto es una sola fase top-level del proyecto (`PHASE5`,
> igual que `PHASE1.md`..`PHASE4-CATALOGO.md`). Las sub-fases internas (Fase 1..7 listadas
> abajo) usan notación decimal en el nombre de archivo de su cierre: `PHASE5.3-*-CIERRE.md`
> (Fase 3 — Ventas), `PHASE5.4-*-CIERRE.md` (Fase 4 — Transferencias),
> `PHASE5.5-*-CIERRE.md` (Fase 5 — Logística), `PHASE5.6-DASHBOARD-CIERRE.md` (Fase 6 —
> Dashboard), `PHASE5.7-DEUDA-TECNICA-CIERRE.md` (Fase 7 — Deuda técnica).

## Objetivo del alcance

Sobre el catálogo (Fase 4, ya cerrado), añadir motor de inventario/movimientos, compras,
ventas, transferencias inter-sucursales, logística, dashboard analítico, y cerrar deuda
técnica (tests MockMvc con JWT real, cobertura de filtro de seguridad, costo promedio
ponderado, validación de stock, recepción parcial).

Esquema DB ya existe completo en `V1__baseline_schema.sql` — **no hizo falta migración
Flyway nueva** para nada de lo listado abajo.

## Decisiones de diseño (vigentes, no reabrir sin razón)

1. **`InventoryMovementPoster`** — único punto interno que toca `inventory.quantity`.
   Bloqueo pesimista por `(branch_id, product_id)`, valida no-negativo, aplica costo
   promedio ponderado solo en `PURCHASE_IN`, gestiona alertas. Compras/ventas/transferencias
   /ajustes son clientes de esto, nunca tocan `inventory` directo.
2. Movimiento manual directo (sin cabecera) vs. `InventoryAdjustment` formal (documento con
   líneas + aprobación).
3. Conversión de unidades al postear: `ProductUnit.conversionFactor`, stock siempre en
   unidad base.
4. `InventoryAlert` = la funcionalidad adicional obligatoria pedida por el enunciado.
5. `Transfer`: máquina de estados en el dominio, `TransferStatusHistory` por transición,
   `TransferIssue` en recepción parcial (RN-09/RN-10), reenvío real fuera de alcance MVP
   (simplificación documentada).
6. Autorización: reutilizar `CurrentUserProvider.requireCanOperateOnBranch` y
   `RoleCode.canApproveTransfers()/canResolveTransferIssues()`, sin tocar el enum.
7. Dashboard = solo consultas de lectura (`DashboardRepositoryPort` + records de proyección
   `SalesSummary`/`ProductRotation`/`BranchComparison`), sin agregado de dominio nuevo.
   Cumplimiento de rutas (HU-36/37) vive en logística, no en dashboard.
8. **Cada `application.service.*Service` lleva `@Primary`.** El `domain.usecase` que envuelve
   y su `@Service` implementan las mismas interfaces `port.in`; sin `@Primary` en el
   `Service`, cualquier autowiring por esa interfaz es ambiguo (`NoUniqueBeanDefinitionException`)
   en cuanto arranca un contexto Spring real. Ver `docs/PHASE5.7-DEUDA-TECNICA-CIERRE.md` para
   el detalle de cómo se detectó. Todo `@Service` nuevo debe llevar `@Primary` desde el inicio.

## Hecho y verificado (compila + tests en verde)

### Fase 1 — Inventario y Movimientos — COMPLETO
Dominio (`Inventory`, `InventoryMovement`, `InventoryAdjustment`+`Item`, `InventoryAlert`,
enums), puertos in/out, `InventoryMovementPoster`, `InventoryService`,
`InventoryAdjustmentService`, `InventoryAlertService`, entidades JPA + mapper + adaptadores
de persistencia, DTOs + mapper web + 3 controladores
(`InventoryController`/`InventoryAdjustmentController`/`InventoryAlertController`).
Tests de dominio y de servicio (incluye `InventoryMovementPosterTest`: RN-04, costo
promedio ponderado, ciclo de vida de alertas). Todo verificado en verde.

### Fase 2 — Compras — COMPLETO
`Supplier`, `PurchaseOrder`+`Item`(+`PurchaseOrderStatus`), servicios, persistencia
(entidades con `AuditableJpaEntity`/`@SuperBuilder`), specs con subquery por `productId`,
DTOs/mapper web/controladores (`SupplierController`, `PurchaseOrderController`). Tests de
dominio + servicio (conversión de unidad box→base, recepción parcial). Todo verificado en
verde.

### Fase 3 — Ventas — COMPLETO
Cierre detallado en `docs/PHASE5.3-VENTAS-CIERRE.md`. RN-03 y la restitución `RETURN_IN`
cubiertas.

### Fase 4 — Transferencias — COMPLETO
Cierre detallado en `docs/PHASE5.4-TRANSFERENCIAS-CIERRE.md`. RN-07/RN-08/RN-09/RN-10
cubiertas.

### Fase 5 — Logística — COMPLETO
Cierre detallado en `docs/PHASE5.5-LOGISTICA-CIERRE.md`. `Carrier`/`LogisticsRoute` CRUD,
`Transfer.assignLogistics`, consulta de cumplimiento por ruta (HU-36/37).

### Fase 6 — Dashboard — COMPLETO
Cierre detallado en `docs/PHASE5.6-DASHBOARD-CIERRE.md`. `SalesSummary`/`ProductRotation`/
`BranchComparison`, `DashboardController` (3 endpoints, `branch-comparison` solo `ADMIN`).

### Fase 7 — Deuda técnica — COMPLETO
Cierre detallado en `docs/PHASE5.7-DEUDA-TECNICA-CIERRE.md`. Resumen:

- **Tres bugs de arranque nunca detectados, corregidos**: mapeo de `country_code` (Hibernate
  exigía `@JdbcTypeCode(SqlTypes.CHAR)` frente a la columna `CHAR(2)`), migración incompleta
  a Spring Boot 4.1/Jackson 3 (`spring-boot-starter-webmvc-test` faltante,
  `SecurityErrorResponder` en `com.fasterxml.jackson` en vez de `tools.jackson`, propiedad
  `write-dates-as-timestamps` en la ruta vieja de `application.yaml`), y ambigüedad de bean
  sistémica entre cada `@Service` y su `@Bean` crudo en `UseCaseConfig` (fix: `@Primary` en
  los 18 `@Service`, ver decisión de diseño #8 arriba). Ninguno se había detectado antes
  porque `InventoriesApplicationTests.contextLoads` llevaba fases enteras fallando "por
  Docker" sin que nadie confirmara que, con Docker disponible, el contexto realmente cargaba.
- **`DataBootstrapper`**: descartado. La clase no existe en código pese a estar diseñada en
  `docs/PHASE3-SEGURIDAD.md` (DEC-16); `CLAUDE.md` confirma que no hay auto-seed de admin.
  No se implementó por estar fuera del alcance de esta fase.
- **`MockMvcTestSupport`** (clase base nueva, JWT real vía `TokenProviderPort`, nunca
  `@WithMockUser`) + tests `MockMvc` para los 9 controladores pedidos: `Auth`, `Branch`,
  `Category`, `Product`, `UnitOfMeasure`, `User`, `Dashboard`, `Carrier`, `LogisticsRoute`.
- **Tests dedicados**: `JwtAuthenticationFilterTest`, `SecurityErrorResponderTest`.
- Compila y verde: **440 tests, 440 en verde** (subía de 359). `./gradlew build` completo
  limpio, incluye `asciidoctor` (`NO-SOURCE`: sin autoría `.adoc` todavía, fuera de alcance).

## Verificación final

```
./gradlew build
```
Confirmado en verde con los 9 controladores nuevos y las correcciones de arranque.

Casos de negocio que los tests demuestran explícitamente:
- RN-03: no vender por encima del stock disponible.
- RN-04: todo cambio de `inventory.quantity` deja fila en `inventory_movement`.
- Costo promedio ponderado recalculado tras compra.
- RN-08: transferencia no despachable sin stock suficiente en origen.
- RN-09/RN-10: recepción parcial refleja cantidad real y genera `TransferIssue`, sin tocar
  destino más allá de lo llegado.
- RN-12/RN-13: alcance de autorización por organización/sucursal, con JWT real de punta a
  punta (no solo mockeado a nivel de servicio).

## Errores ya resueltos en este trabajo (no repetir)

- `@Builder` + `@Builder.Default` + `@NoArgsConstructor` sin `@AllArgsConstructor` no
  compila cuando la entidad NO usa `@SuperBuilder`. Con `@SuperBuilder` (entidades que
  extienden `AuditableJpaEntity`) no hace falta `@AllArgsConstructor` explícito.
- Mappers de persistencia son clases `@Component` planas (métodos normales), NO interfaces
  MapStruct — un `@Mapper` con solo métodos `default` puede no generar bean Spring.
- En tests Mockito, nunca envolver `ArgumentMatchers.eq(...)` dentro de un método helper
  propio pasado a `verify(...)` — rompe la pila de matchers. Inlinear directo.
- Revisar columnas reales de la migración V1 por tabla antes de decidir si una entidad JPA
  extiende `AuditableJpaEntity` (created_at+updated_at) o no (ej. `sale` solo tiene
  `created_at`).
- Una columna con `columnDefinition` no estándar (ej. `CHAR(n)`) necesita también
  `@JdbcTypeCode(SqlTypes.CHAR)` (u otro tipo Hibernate explícito): el `columnDefinition`
  literal no basta para pasar la validación de esquema en Hibernate 6+.
- En Spring Boot 4.1, `spring-boot-starter-test` no trae el slice MockMvc — hace falta
  `spring-boot-starter-webmvc-test` aparte, y su `@AutoConfigureMockMvc` vive en
  `org.springframework.boot.webmvc.test.autoconfigure`, no en `...test.autoconfigure.web.servlet`.
- En Spring Boot 4.1, el `ObjectMapper` autoconfigurado por defecto es
  `tools.jackson.databind.ObjectMapper` (Jackson 3), no `com.fasterxml.jackson.databind.ObjectMapper`
  (Jackson 2) — cualquier clase propia que dependa de un bean `ObjectMapper` debe importar el
  tipo nuevo, o el contexto falla con `NoSuchBeanDefinitionException`.
- `spring.jackson.serialization.write-dates-as-timestamps` no existe en Jackson 3: se mueve a
  `spring.jackson.datatype.datetime.write-dates-as-timestamps`.
- Todo `application.service.*Service` necesita `@Primary`: sin ella, es ambiguo contra el
  `@Bean` crudo del `domain.usecase` que envuelve en cuanto algo autowired por la interfaz
  `port.in` que ambos implementan.
- `TestcontainersConfiguration` debe ser `public` si algún test fuera del paquete raíz
  (`io.github.KevinMitsi.inventories`) necesita importarla.

## Cómo retomar

El plan de Fase 5 (Fases 1-7) está completo. Si se abre trabajo nuevo sobre este módulo:

1. Leer este archivo completo y `docs/PHASE5.7-DEUDA-TECNICA-CIERRE.md` para el estado y las
   correcciones de arranque ya aplicadas — no reabrirlas sin motivo.
2. Definir el alcance del trabajo nuevo (¿autoría de Spring REST Docs? ¿`MockMvc` para los
   controladores de Fases 1-5 no cubiertos? ¿una fase de producto distinta?) y documentarlo
   en un `PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V8.md` nuevo, no editando este archivo.
3. Compilar y correr tests después de cada módulo, no acumular trabajo sin verificar.
