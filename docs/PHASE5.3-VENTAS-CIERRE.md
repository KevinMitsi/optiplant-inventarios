# Fase 3 (Ventas) — Cierre

> Cerrada 2026-08-28, retomando desde el estado dejado en `docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS.md`
> ("Fase 3 — Ventas — PARCIAL (~65%)"). Este documento cubre solo lo hecho en esta sesión para
> terminar la fase; el resto del dominio (`Sale`, `SaleUseCase`, `PriceListUseCase`, persistencia
> de listas de precios, etc.) ya venía de una sesión anterior y no se repite aquí.

## Qué se hizo

1. **Persistencia** (lo único que faltaba en esta capa):
   - `ProductPricePersistenceAdapter` — implementa `ProductPriceRepositoryPort`: `save` y
     `findByPriceListIdAndProductIdAndProductUnitId`. Sin paginación, CRUD puntual.
   - `SalePersistenceAdapter` — implementa `SaleRepositoryPort` vía `SalesSpecifications.forSales`
     + `PageQueryTranslator`, igual que `PriceListPersistenceAdapter`.
2. **Web** (contrato HTTP completo):
   - `PriceListDtos` — `CreatePriceListRequest`, `UpdatePriceListRequest`,
     `SetProductPriceRequest`, `PriceListResponse`, `ProductPriceResponse`.
   - `SaleDtos` — `CreateSaleRequest` (+ `ItemRequest` anidado), `SaleResponse` (+ `ItemResponse`
     anidado, incluye `total`).
   - `SalesWebMapper` — MapStruct, mismo patrón que `PurchasingWebMapper`: métodos `default`
     para construir comandos desde el request (porque los comandos no son beans que MapStruct
     pueda mapear 1:1), mapeo automático para las respuestas, qualifiers `map(Money)`,
     `map(Quantity)`, `map(Percentage)` → `BigDecimal`. El campo `total` de `SaleResponse` se
     resuelve con `@Mapping(target = "total", expression = "java(map(sale.total()))")` porque
     `Sale.total()` es un método de cálculo, no un getter de campo.
   - `PriceListController` — rutas `/organizations/{organizationId}/price-lists` (crear/listar,
     alcance de organización) y `/price-lists/{priceListId}` (consultar/actualizar/activar/
     desactivar/fijar-consultar precio de producto). Autorización con
     `CurrentUserProvider.requireBelongsToOrganization`, roles `ADMIN`/`BRANCH_MANAGER` para
     escritura (igual que `CategoryController`, porque una lista de precios es un recurso de
     organización, no de sucursal).
   - `SaleController` — rutas `/branches/{branchId}/sales` (crear/listar, alcance de sucursal) y
     `/sales/{saleId}` (consultar/confirmar/cancelar). Autorización con
     `CurrentUserProvider.requireCanOperateOnBranch`, roles `ADMIN`/`BRANCH_MANAGER`/
     `INVENTORY_OPERATOR` (igual que `PurchaseOrderController`).
3. **Tests**:
   - `domain/model/SaleTest.java` — ciclo de vida (nace `DRAFT`, no confirma/cancela dos veces,
     rechaza venta sin líneas), `wasConfirmed()`, y `total()` sumando subtotales netos con
     descuento por línea.
   - `domain/usecase/SaleServiceTest.java` — nombrada así seguiendo el patrón real ya existente
     en el repo (`PurchaseOrderServiceTest`, aunque vive en el paquete `domain.usecase` y prueba
     `SaleUseCase` directamente, no la capa `application.service`; esa capa es un delegado 1:1
     sin lógica propia, así que no aporta nada probarla aparte). Cubre:
     - RN-03: `confirmSale` propaga `InsufficientStockException` cuando el mock de
       `InventoryMovementPoster` la lanza.
     - `confirmSale` postea `SALE_OUT` por línea.
     - cancelar una venta `CONFIRMED` postea `RETURN_IN` compensatorio por línea.
     - cancelar una venta `DRAFT` no postea ningún movimiento.
   - `domain/usecase/PriceListServiceTest.java` — mismo patrón. Cubre creación (+ rechazo de
     código duplicado), actualización, activar/desactivar, y el upsert de `ProductPrice`
     (crea si no existe, reemplaza si ya existía para esa presentación).

## Por qué estas decisiones

- **Tests contra `domain.usecase`, no `application.service`**: así está todo el resto del
  repo (ver `PurchaseOrderServiceTest`, que en realidad instancia `PurchaseOrderUseCase`). La
  capa `application.service` es un wrapper `@Transactional` que delega 1:1; probarla aparte
  solo repetiría los mismos casos sin añadir cobertura real. El plan original (`PHASE5-...md`)
  pedía los tests bajo `application.service.*Test`, pero se siguió la convención real del
  código en vez de la literal del plan.
- **`SalesWebMapper` con métodos `default` para comandos**: mismo motivo que
  `PurchasingWebMapper` — los `Command` son records con forma distinta al DTO de entrada
  (agregan `branchId`/`organizationId`/`createdBy` que vienen de la ruta o del usuario
  autenticado, no del body), así que un mapeo automático de MapStruct no aplica.
- **Roles y alcance en los controladores**: `PriceList` cuelga de `organizationId` (una lista
  de precios puede aplicar a varias sucursales), así que usa
  `requireBelongsToOrganization` como `CategoryController`. `Sale` cuelga de `branchId` (una
  venta ocurre en una sucursal concreta, RN-02), así que usa `requireCanOperateOnBranch` como
  `PurchaseOrderController`.

## Verificación

```
./gradlew.bat compileJava        # limpio
./gradlew.bat compileTestJava    # limpio
./gradlew.bat test --tests "*.domain.model.SaleTest" --tests "*.domain.usecase.SaleServiceTest" --tests "*.domain.usecase.PriceListServiceTest"
```
17 tests, `failures="0" errors="0"` en los XML de `build/test-results/test/`.

`./gradlew.bat test` (suite completa): 311 tests, 310 en verde. La única falla es
`InventoriesApplicationTests.contextLoads` — `Could not find a valid Docker environment` de
Testcontainers, porque Docker no estaba corriendo en la máquina al ejecutar. No es una
regresión de este trabajo; para confirmarlo, levantar Docker Desktop y volver a correr
`./gradlew.bat test --tests "*.InventoriesApplicationTests"`.

## Qué falta (para la próxima sesión)

Fase 3 queda cerrada del todo. Lo siguiente en el plan, en orden, es:

- **Fase 4 — Transferencias** (NO INICIADO): `Transfer`, `TransferItem`,
  `TransferStatusHistory`, `TransferIssue` + enums. Máquina de estados de 5 pasos (solicitar →
  aprobar → preparar → despachar → recibir), con `TransferIssue` en recepción parcial. Detalle
  completo en `docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS.md`, sección "Fase 4".
- Fase 5 (Logística), Fase 6 (Dashboard), Fase 7 (deuda técnica de cierre) siguen después, en
  ese orden — ver el documento maestro.

Para retomar: leer `docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS.md` completo (tiene las
decisiones de diseño vigentes y los errores ya resueltos que no hay que repetir) y empezar
directo por la Fase 4.
