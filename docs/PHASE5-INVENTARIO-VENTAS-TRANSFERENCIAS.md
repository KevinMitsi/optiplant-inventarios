# Fase 5 — Inventario, Compras, Ventas, Transferencias, Logística, Dashboard

> Estado a 2026-08-27. Plan aprobado original: `snazzy-snacking-peach.md` (guardado en
> `~/.claude/plans/`). Este documento reemplaza esa referencia efímera con una que vive en
> el repo y sirve para retomar en otra sesión.

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

### Fase 3 — Ventas — PARCIAL (~65%)
**Hecho:**
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

**Pendiente — retomar aquí:**
1. `ProductPricePersistenceAdapter` (implementa `ProductPriceRepositoryPort`; no necesita
   paginación, solo CRUD + `findByPriceListIdAndProductIdAndProductUnitId`).
2. `SalePersistenceAdapter` (implementa `SaleRepositoryPort` vía `SalesSpecifications.forSales`).
3. Web: `PriceListDtos.java`, `SaleDtos.java`, `SalesWebMapper.java` (MapStruct, seguir
   patrón `PurchasingWebMapper`/`InventoryWebMapper` — qualifiers para `Money`/`Quantity`/
   `Percentage`).
4. Web: `PriceListController.java`, `SaleController.java` (Swagger annotations, igual que
   controladores de Fases 1-2).
5. Tests dominio: `SaleTest.java` (opcional `PriceListTest.java`).
6. Tests aplicación: `SaleServiceTest.java` — **crítico**, debe cubrir:
   - RN-03: `confirmSale` propaga `InsufficientStockException` desde
     `InventoryMovementPoster` cuando no hay stock suficiente.
   - cancelar venta ya `CONFIRMED` → postea `RETURN_IN` compensatorio por cada línea.
   - `PriceListServiceTest.java` (CRUD + upsert de `ProductPrice`).
7. Compilar y verificar:
   ```
   ./gradlew.bat compileJava
   ./gradlew.bat compileTestJava
   ./gradlew.bat test --tests "*.domain.model.Sale*Test" --tests "*.application.service.*Sale*Test" --tests "*.application.service.PriceListServiceTest"
   ```
   Confirmar `failures="0" errors="0"` en los XML de `build/test-results/test/`.

### Fase 4 — Transferencias — NO INICIADO
`Transfer`, `TransferItem`, `TransferStatusHistory`, `TransferIssue` + enums
(`TransferStatus`, `TransferPriority`, `TransferIssueType`, `TransferIssueResolution`).
Máquina de estados según decisión de diseño #5. 5 pasos: solicitar → aprobar → preparar →
despachar (postea `TRANSFER_OUT`, valida stock origen RN-08) → recibir completo/parcial
(postea `TRANSFER_IN` por cantidad real, RN-09; genera `TransferIssue` si `received <
shipped`, RN-10) → resolver incidencia. Autorización: origen para solicitar/despachar,
destino para recibir. Puertos, servicio, persistencia, web, tests — patrón idéntico a Fases
1-3.

### Fase 5 — Logística — NO INICIADO
`Carrier`, `LogisticsRoute` CRUD (patrón `CategoryService`/`SupplierService`). Consulta de
cumplimiento por ruta (HU-36/37): estimado (`estimated_duration_minutes`/
`estimated_arrival_at`) vs. real (`shipped_at`/`received_at`) agregando sobre `transfer`.

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
2. Ir directo a "Fase 3 — Ventas — PARCIAL", punto 1 de "Pendiente".
3. Seguir el orden de fases 3(resto)→4→5→6→7 tal como está aquí; no reabrir decisiones de
   diseño salvo que aparezca un conflicto real con el esquema o el dominio.
4. Compilar y correr tests después de cada módulo, no acumular fases sin verificar.
