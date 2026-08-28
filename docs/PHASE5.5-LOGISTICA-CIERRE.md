# Fase 5 (Logística) — Cierre

> Cerrada 2026-08-28, en la misma sesión que cerró la Fase 4 (Transferencias). No había nada
> empezado (`docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS.md` la marcaba "NO INICIADO").

## Qué se hizo

**Dominio** (`domain/model`):
- `Carrier` — transportista con baja lógica, mismo patrón que `Supplier` (`code` único por
  organización, `deactivate`/`activate`).
- `LogisticsRoute` — ruta habitual entre dos sucursales (ENTITIES.md §16.2): valida
  RN-07 (origen ≠ destino, igual que `Transfer`), duración estimada positiva, `estimatedCost`
  opcional como `Money` (que ya garantiza no-negativo por construcción, así que no hace falta
  revalidar el signo en `LogisticsRoute`). `connects(origin, destination)` es lo que usa
  `Transfer.assignLogistics` para comprobar que la ruta elegida corresponde de verdad al
  trayecto de la transferencia.
- `RouteComplianceSummary` — proyección de solo lectura para HU-36/HU-37 (no es un agregado):
  por ruta, cuántas transferencias completadas tuvo, su duración real promedio y la fracción
  que llegó a tiempo frente al estimado de la ruta.
- `Transfer.assignLogistics(carrierId, routeId, estimatedArrivalAt)` — nuevo método de
  dominio (pendiente explícito de Fase 4): fija transportista/ruta/llegada estimada, solo
  válido en `REQUESTED`/`APPROVED`/`IN_PREPARATION` (antes de despachar). Añadió los tres
  campos correspondientes al agregado, con getters, y extendió `create`/`reconstitute`.

**Aplicación** (`application/port/in|out`, `domain/usecase`):
- Comandos: `CreateCarrierCommand`, `UpdateCarrierCommand`, `CreateLogisticsRouteCommand`,
  `UpdateLogisticsRouteCommand`, `AssignTransferLogisticsCommand`.
- `ManageCarrierUseCase`/`QueryCarrierUseCase` implementados por `CarrierUseCase` (mismo
  esqueleto que `SupplierUseCase`); `ManageLogisticsRouteUseCase`/`QueryLogisticsRouteUseCase`
  implementados por `LogisticsRouteUseCase` (valida organización + ambas sucursales al crear,
  y unicidad de `(origin, destination)`; `getRouteCompliance` valida solo que la organización
  exista).
- `TransferUseCase.assignLogistics`: carga la transferencia, valida que el transportista
  exista, carga la ruta y comprueba `route.connects(origin, destination)` antes de delegar en
  `Transfer.assignLogistics` — la validación de "la ruta es la que corresponde" vive en el
  use case (necesita las dos entidades cargadas), no en el dominio de `Transfer` (que no
  conoce `LogisticsRoute`).

**Persistencia**: `CarrierJpaEntity`/`LogisticsRouteJpaEntity` (ambas `AuditableJpaEntity`,
mismo patrón que `SupplierJpaEntity`), `LogisticsPersistenceMapper` (plano, no MapStruct,
mismo motivo que el resto del repo), `LogisticsSpecifications` (`forCarriers`/
`forLogisticsRoutes`), `CarrierPersistenceAdapter`/`LogisticsRoutePersistenceAdapter`.
`TransferJpaEntity` ahora mapea `carrier_id`/`route_id`/`estimated_arrival_at` (antes
deliberadamente `NULL`, ver cierre de Fase 4); `TransferPersistenceMapper` actualizado en los
dos sentidos.

El cumplimiento por ruta (`LogisticsRouteJpaRepository.findComplianceByOrganizationId`) es una
consulta **nativa**, no JPQL: agrega `transfer` (tiempo real de tránsito,
`received_at - shipped_at` en minutos vía `EXTRACT(EPOCH FROM ...)`) agrupado por
`logistics_route`, comparando contra `estimated_duration_minutes`. Nativa por el mismo motivo
que ya justificó `TransferIssueJpaEntity.transferItemId` en el cierre de Fase 4: agrega sobre
otro agregado (`Transfer`) sin relación JPA cruzada. Una interfaz de proyección
(`RouteComplianceRow`) recoge el resultado plano; el adaptador lo traduce a
`RouteComplianceSummary`, calculando `onTimeRate` (`null` si la ruta no tiene transferencias
completadas, para no confundir "0% de cumplimiento" con "sin datos").

**Web**: `CarrierDtos`/`LogisticsRouteDtos`, `LogisticsWebMapper` (MapStruct con métodos
`default` para los comandos que no son 1:1, igual que `PurchasingWebMapper`),
`CarrierController` (`/organizations/{organizationId}/carriers`, `/carriers/{id}`,
`/carriers/{id}/deactivation|activation`) y `LogisticsRouteController`
(`/organizations/{organizationId}/logistics-routes`,
`/organizations/{organizationId}/logistics-routes/compliance` para HU-36/37,
`/logistics-routes/{id}`, `/logistics-routes/{id}/deactivation|activation`).
`TransferController` ganó `POST /transfers/{id}/logistics-assignment`; `TransferDtos` ganó
`AssignTransferLogisticsRequest` y `TransferResponse` ahora incluye `carrierId`/`routeId`/
`estimatedArrivalAt`.

**Tests**: `domain/model/TransferTest` (nested `LogisticsAssignment`: asigna antes de
despachar, rechaza después de despachar). `domain/usecase/CarrierServiceTest`,
`domain/usecase/LogisticsRouteServiceTest` (mismo patrón que `SupplierServiceTest`: alta,
duplicado, recurso inexistente; además RN-07 para la ruta). `domain/usecase/
TransferServiceTest` ganó un nested `LogisticsAssignment` (asigna cuando la ruta conecta
origen/destino; rechaza una ruta que no conecta). 346 tests en total, todos verdes salvo
`InventoriesApplicationTests.contextLoads` (Docker no disponible localmente, no relacionada).

## Por qué estas decisiones

- **`assignLogistics` no valida "la ruta es correcta" dentro de `Transfer`**: el agregado
  `Transfer` no tiene ninguna referencia a `LogisticsRoute` (mismo principio de no cargar un
  agregado desde otro, ya aplicado en toda la Fase 4). Comprobar que la ruta elegida conecta
  el origen/destino reales exige tener ambas entidades cargadas — vive en `TransferUseCase`,
  que sí las tiene.
- **`Money` no revalida negativos en `LogisticsRoute`**: `Money` ya rechaza importes
  negativos en su propio constructor; repetir la validación en el dominio de la ruta sería
  código muerto que nunca se ejecuta, así que se quitó (quedó solo el mensaje de validación
  del DTO, `@PositiveOrZero`, como primera línea de defensa amigable en el borde HTTP).
- **`getRouteCompliance` es por organización, no por ruta individual**: HU-36/37 piden
  comparar/analizar rutas entre sí ("identificar cuáles son las más eficientes"), que exige
  verlas juntas. Un cliente que solo quiera una ruta puede filtrar la lista por `routeId` en
  el resultado.
- **`onTimeRate`/`averageActualMinutes` son `Double` nulable, no `0.0`**: una ruta sin
  transferencias completadas no tiene "0% de cumplimiento", tiene "sin datos todavía" — son
  estados distintos que un cliente necesita poder distinguir.

## Verificación

```
./gradlew.bat compileJava        # limpio
./gradlew.bat compileTestJava    # limpio
./gradlew.bat test --tests "*.domain.model.TransferTest" --tests "*.domain.usecase.TransferServiceTest" --tests "*.domain.usecase.CarrierServiceTest" --tests "*.domain.usecase.LogisticsRouteServiceTest"
```
Todos verdes.

`./gradlew.bat test` (suite completa): 346 tests, 345 en verde. Única falla:
`InventoriesApplicationTests.contextLoads` (`Could not find a valid Docker environment` de
Testcontainers) — no relacionada con este trabajo, Docker no estaba corriendo al ejecutar.

## Qué falta (para la próxima sesión)

Fase 5 queda cerrada del todo. Lo siguiente en el plan, en orden:

- **Fase 6 — Dashboard** (NO INICIADO): records de proyección (`SalesSummary`,
  `ProductRotation`, `BranchComparison`, etc.), `DashboardRepositoryPort` con `@Query`
  JPQL/nativas, servicio de solo lectura, `DashboardController`. Sin agregado de dominio
  nuevo.
- Fase 7 (deuda técnica de cierre) sigue después: tests unitario+`MockMvc` incremental de
  cada controlador de Fase 5 (`CarrierController`/`LogisticsRouteController`, si no se hizo
  ya) y de Fase 6, más el cierre final descrito en el documento maestro (tests `MockMvc` con
  JWT real para los 6 controladores sin cobertura: Auth, Branch, Category, Product,
  UnitOfMeasure, User).

Para retomar: leer `docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V5.md` completo (decisiones de
diseño vigentes y errores ya resueltos) y empezar directo por la Fase 6. Al cerrar Fase 6, crear
`docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V6.md` en vez de editar este archivo (ver nota de
versionado al inicio del V5).
