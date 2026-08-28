# Fase 5 — Inventario, Compras, Ventas, Transferencias, Logística, Dashboard

> Estado a 2026-08-28. Plan aprobado original: `snazzy-snacking-peach.md` (guardado en
> `~/.claude/plans/`). Este documento reemplaza esa referencia efímera con una que vive en
> el repo y sirve para retomar en otra sesión. Ver también `docs/PHASE5.3-VENTAS-CIERRE.md`
> y `docs/PHASE5.4-TRANSFERENCIAS-CIERRE.md` para el detalle de cómo se cerraron las
> Fases 3 y 4.
>
> **Versionado**: este archivo NO se sobreescribe en el mismo nombre al cerrar una fase — el
> sufijo `-V{n}` sube (`n` = número de la última fase cerrada al momento del snapshot) y el
> archivo anterior se conserva para dejar rastro auditable de la evolución del plan. Este es
> `V5` (snapshot tras cerrar Fase 5 — Logística). El siguiente cierre (Fase 6) debe crear
> `PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V6.md`, no editar este archivo in-place.
>
> **Numeración de archivos**: todo esto es una sola fase top-level del proyecto (`PHASE5`,
> igual que `PHASE1.md`..`PHASE4-CATALOGO.md`). Las sub-fases internas (Fase 1..7 listadas
> abajo) usan notación decimal en el nombre de archivo de su cierre: `PHASE5.3-*-CIERRE.md`
> (Fase 3 — Ventas), `PHASE5.4-*-CIERRE.md` (Fase 4 — Transferencias),
> `PHASE5.5-*-CIERRE.md` (Fase 5 — Logística). Fase 6 → `PHASE5.6-DASHBOARD-CIERRE.md`.

## Objetivo del alcance

Sobre el catálogo (Fase 4, ya cerrado), añadir motor de inventario/movimientos, compras,
ventas, transferencias inter-sucursales, logística, dashboard analítico, y cerrar deuda
técnica (tests MockMvc con JWT real, cobertura de filtro de seguridad, costo promedio
ponderado, validación de stock, recepción parcial).

Esquema DB ya existe completo en `V1__baseline_schema.sql` — **no hace falta migración
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
Cierre detallado en `docs/PHASE5.3-VENTAS-CIERRE.md`. Resumen: persistencia
(`ProductPricePersistenceAdapter`, `SalePersistenceAdapter`), web (`PriceListDtos`, `SaleDtos`,
`SalesWebMapper`, `PriceListController`, `SaleController`), tests de dominio (`SaleTest`) y de
caso de uso (`SaleServiceTest`, `PriceListServiceTest`, ambos en `domain.usecase` siguiendo el
patrón real del repo, no `application.service`). RN-03 y la restitución `RETURN_IN` cubiertas.
Compila y verde (`compileJava`, `compileTestJava`, tests dirigidos). `./gradlew test` completo:
310/311 en verde, la única falla es `InventoriesApplicationTests.contextLoads` por falta de
Docker local (Testcontainers) — no relacionada con este trabajo.

**Hecho (histórico, ya cubierto arriba):**
- Dominio completo: `SaleStatus`, `PriceList`, `ProductPrice`, `SaleItem`, `Sale`
  (`confirm()`, `cancel()`, `total()`).
- Aplicación completa: comandos (`CreatePriceListCommand`, `UpdatePriceListCommand`,
  `SetProductPriceCommand`, `CreateSaleCommand`), search criteria, use cases
  (`ManagePriceListUseCase`, `QueryPriceListUseCase`, `ManageSaleUseCase`,
  `QuerySaleUseCase`), puertos out (`PriceListRepositoryPort`, `ProductPriceRepositoryPort`,
  `SaleRepositoryPort`), `PriceListService`, `SaleService` (createSale/confirmSale delega
  RN-03 a `InventoryMovementPoster`; cancelSale postea `RETURN_IN` compensatorio si ya
  estaba `CONFIRMED`).
- Persistencia — entidades: `PriceListJpaEntity`, `ProductPriceJpaEntity`,
  `SaleItemJpaEntity`, `SaleJpaEntity` (ojo: `Sale` NO extiende `AuditableJpaEntity`, tabla
  solo tiene `created_at`).
- Persistencia — repos: `PriceListJpaRepository`, `ProductPriceJpaRepository`,
  `SaleJpaRepository` (`@EntityGraph` en `findById`), `SalesSpecifications`.
- Persistencia — mapper: `SalesPersistenceMapper` (plain `@Component`, patrón ya
  establecido en Fases 1-2, NO MapStruct).
- Persistencia — adaptador: `PriceListPersistenceAdapter` (implementa
  `PriceListRepositoryPort` vía `PageQueryTranslator` + `SalesSpecifications`).

**Pendiente:** nada. Fase cerrada, ver `docs/PHASE5.3-VENTAS-CIERRE.md`.

### Fase 4 — Transferencias — COMPLETO
Cierre detallado en `docs/PHASE5.4-TRANSFERENCIAS-CIERRE.md`. Resumen: dominio
(`Transfer`+`TransferItem` agregado con máquina de estados de 5 pasos, `TransferIssue` y
`TransferStatusHistory` como agregados independientes, 4 enums), aplicación (comandos, puertos
in/out, `TransferUseCase`/`TransferIssueUseCase`), persistencia (4 entidades JPA, specs,
mapper, 3 adaptadores), web (`TransferController`, `TransferIssueController`), tests de
dominio (`TransferTest`) y de caso de uso (`TransferServiceTest`, `TransferIssueServiceTest`).
RN-07/RN-08/RN-09/RN-10 cubiertas. Asignar transportista/ruta queda fuera de esta fase
(diferido a Fase 5, Logística). Compila y verde: 334/335 tests (única falla,
`InventoriesApplicationTests.contextLoads`, es por Docker no disponible localmente, no
relacionada con este trabajo).

### Fase 5 — Logística — COMPLETO
Cierre detallado en `docs/PHASE5.5-LOGISTICA-CIERRE.md`. Resumen: `Carrier`/
`LogisticsRoute` CRUD (patrón `SupplierService`), `Transfer.assignLogistics` (nuevo método de
dominio, solo antes de despachar) con `carrier_id`/`route_id`/`estimated_arrival_at` ya
mapeados en `TransferJpaEntity`, y consulta de cumplimiento por ruta (HU-36/37) vía consulta
nativa agregando `transfer` sobre `logistics_route`. Compila y verde: 346 tests, única falla
`InventoriesApplicationTests.contextLoads` (Docker no disponible localmente, no relacionada).

### Fase 6 — Dashboard — NO INICIADO
Records de proyección en `domain/model` (`SalesSummary`, `ProductRotation`,
`BranchComparison`, etc.), `DashboardRepositoryPort` con `@Query` JPQL/nativas, servicio de
consulta de solo lectura, `DashboardController`. Sin agregado de dominio nuevo.

### Fase 7 — Deuda técnica
- **Incremental** (hacer junto con cada fase, no al final): test unitario de cada servicio
  nuevo + test `MockMvc` de cada controlador nuevo (Fases 3 resto, 4, 5, 6).
- **Cierre final** (al terminar todo lo anterior):
  - Tests `MockMvc` para los 6 controladores existentes sin cobertura: Auth, Branch,
    Category, Product, UnitOfMeasure, User. Deben usar **JWT real** emitido por
    `TokenProviderPort` (NO `@WithMockUser`) — requisito explícito del usuario: "ejercitar
    la cadena de seguridad completa". Base: `@SpringBootTest` + `@AutoConfigureMockMvc` +
    Testcontainers, como `InventoriesApplicationTests`.
  - Tests dedicados: `JwtAuthenticationFilter`, `SecurityErrorResponder`,
    `DataBootstrapper`.

## Verificación final (cuando todo lo anterior esté cerrado)

```
./gradlew build
```
(build completo, incluye Spring REST Docs/Asciidoctor — confirmar que no rompe con los
controladores nuevos).

Casos de negocio que los tests deben demostrar explícitamente, todos ya cubiertos en fases
1-2, pendientes en 3-4:
- RN-03: no vender por encima del stock disponible.
- RN-04: todo cambio de `inventory.quantity` deja fila en `inventory_movement`.
- Costo promedio ponderado recalculado tras compra.
- RN-08: transferencia no despachable sin stock suficiente en origen.
- RN-09/RN-10: recepción parcial refleja cantidad real y genera `TransferIssue`, sin tocar
  destino más allá de lo llegado.

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

## Cómo retomar

1. Leer este archivo completo.
2. Ir directo a "Fase 6 — Dashboard — NO INICIADO".
3. Seguir el orden de fases 6→7 tal como está aquí; no reabrir decisiones de diseño
   salvo que aparezca un conflicto real con el esquema o el dominio.
4. Compilar y correr tests después de cada módulo, no acumular fases sin verificar.
