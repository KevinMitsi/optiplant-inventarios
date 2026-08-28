# Fase 5.6 — Dashboard (cierre)

> Estado a 2026-08-28. Cierra "Fase 6 — Dashboard" listada en
> `docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V5.md`. Ver ese archivo para el plan completo
> de la Fase 5 top-level y las decisiones de diseño vigentes (en particular la #7, que fija el
> alcance de esta sub-fase).

## Alcance

RF-42..RF-47 / HU-38..HU-42 (EP-09, Dashboard y analítica), solo lectura, sin agregado de
dominio nuevo. HU-40 (stock crítico) y HU-41 (transferencias activas) ya estaban cubiertas por
`InventoryAlertController`/`InventoryController` y `TransferController` respectivamente — no
requirieron trabajo nuevo, solo se confirmó que ya cumplen esos requisitos.

Tres proyecciones nuevas en `domain.model`, todas records de solo lectura (mismo criterio que
`RouteComplianceSummary` de logística):

- **`SalesSummary`** (RF-42/43, HU-38): ventas confirmadas agrupadas por mes calendario y
  sucursal, para comparar el mes actual contra meses anteriores.
- **`ProductRotation`** (RF-44, HU-39): cantidad vendida por producto en un período, de mayor a
  menor demanda; los productos sin ventas en el período aparecen al final con cantidad cero
  (para que "baja rotación" incluya también los que no rotaron nada, no solo los últimos con
  ventas).
- **`BranchComparison`** (RF-47, HU-42): por sucursal, ventas confirmadas de los últimos 30
  días, valor de inventario a costo promedio ponderado, y productos en o por debajo de su stock
  mínimo. Reservado al rol `ADMIN` (RN-12) vía `@PreAuthorize("hasRole('ADMIN')")`.

## Cadena implementada

- `application.port.in.QueryDashboardUseCase` / `application.port.out.DashboardRepositoryPort`
  — mismos tres métodos de lectura en ambos, `organizationId` obligatorio, `branchId` opcional
  (nulo agrega toda la organización) en los dos primeros.
- `domain.usecase.DashboardUseCase` — valida que la organización exista y delega al puerto.
  Sin lógica de negocio adicional: es una proyección, no un agregado.
- `application.service.DashboardService` — wrapper `@Transactional` delegando 1:1, patrón
  estándar del repo.
- `infrastructure.adapter.persistence.repository.DashboardJpaRepository` — interfaz marcador
  `Repository<SaleJpaEntity, UUID>` (no `JpaRepository`: el dashboard no tiene agregado propio,
  así que no expone CRUD) con tres `@Query` nativas:
  - `getSalesSummary`: `sale` + `sale_item` agrupado por mes/sucursal, solo `CONFIRMED`.
  - `getProductRotation`: CTE `period_item` (ventas confirmadas del período) + `LEFT JOIN`
    desde `product`, para que los productos sin ventas también aparezcan.
  - `getBranchComparison`: dos subconsultas (`sale`+`sale_item` de últimos 30 días; `inventory`
    agregado) unidas por `LEFT JOIN` a `branch`, mismo criterio que el índice parcial
    `ix_inventory_low_stock` para "stock crítico".
- `infrastructure.adapter.persistence.DashboardPersistenceAdapter` — traduce las proyecciones
  planas (`SalesSummaryRow`/`ProductRotationRow`/`BranchComparisonRow`) a los records de
  dominio, patrón idéntico a `LogisticsRoutePersistenceAdapter.getRouteCompliance`.
- `infrastructure.adapter.web` — `DashboardDtos` (records `*Response`), `DashboardWebMapper`
  (MapStruct, igual que `LogisticsWebMapper`), `DashboardController` con tres endpoints bajo
  `/api/v1/organizations/{organizationId}/dashboard`:
  - `GET /sales-summary?branchId=&from=&to=`
  - `GET /product-rotation?branchId=&from=&to=`
  - `GET /branch-comparison` (solo `ADMIN`)

  `from`/`to` opcionales (`Instant`, ISO-8601); sin período explícito se usan los últimos 6
  meses. Todos exigen `requireBelongsToOrganization` (mismo patrón de autorización que
  `LogisticsRouteController`).
- `infrastructure.config.UseCaseConfig` — bean `dashboardUseCase(...)` con wiring manual.

No hizo falta migración Flyway: las tres consultas nativas leen tablas ya existentes desde
`V1__baseline_schema.sql` (que ya trae los índices pensados para esto —
`ix_sale_branch_date`, `ix_sale_item_product`, `ix_inventory_low_stock` — con comentarios que
referencian estas mismas HUs).

## Tests

`DashboardUseCaseTest` (`domain.usecase`, patrón `LogisticsRouteServiceTest`): delega cada uno
de los tres métodos tras validar la organización, y falla con `ResourceNotFoundException` si la
organización no existe. No se agregó test `MockMvc` para `DashboardController` — no hay
ninguno en el repo todavía para ningún controlador (incluida logística, ya cerrada); esa deuda
queda consolidada en la Fase 7, como ya estaba.

## Verificado

`./gradlew compileJava compileTestJava` limpio. `./gradlew test`: 350 tests, 349 en verde; la
única falla es `InventoriesApplicationTests.contextLoads` por falta de Docker local
(Testcontainers), no relacionada con este trabajo — mismo estado que en el cierre de fases
anteriores.

## Pendiente

Nada de Fase 6. Sigue Fase 7 — Deuda técnica (ver
`docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V5.md` para el detalle: tests `MockMvc` con JWT
real para los 6 controladores sin cobertura + los 3 nuevos de esta fase, cobertura de
`JwtAuthenticationFilter`/`SecurityErrorResponder`/`DataBootstrapper`, y verificación final con
`./gradlew build` completo incluyendo Asciidoctor).
