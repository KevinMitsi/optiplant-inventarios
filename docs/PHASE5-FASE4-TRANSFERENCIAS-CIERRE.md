# Fase 4 (Transferencias) — Cierre

> Cerrada 2026-08-28, en la misma sesión que cerró la Fase 3 (Ventas). Fase completa desde
> cero: no había nada empezado (`docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS.md` la marcaba
> "NO INICIADO").

## Qué se hizo

**Dominio** (`domain/model`):
- `TransferStatus`, `TransferPriority`, `TransferIssueType`, `TransferIssueResolution` — los
  4 enums de catálogo (ENTITIES.md §13.1/13.2/15.1/15.2), con `fromString` igual que el resto
  del dominio.
- `TransferItem` — línea con 4 cantidades encadenadas (`requested ≥ approved ≥ shipped ≥
  received`), cada una fijada por su propio método (`approve`/`ship`/`receive`), todos
  package-private porque solo `Transfer` los invoca. `missingQuantity()` deriva el faltante
  sin persistirlo (ENTITIES.md §13.6).
- `Transfer` — agregado raíz con máquina de estados de 5 pasos: `create` (REQUESTED) →
  `approve` (APPROVED, HU-29) → `startPreparation` (IN_PREPARATION) → `dispatch`
  (IN_TRANSIT) → `receive` (RECEIVED o PARTIALLY_RECEIVED, según si alguna línea quedó con
  faltante). También `cancel()` (solo antes de despachar) y `close()` (PARTIALLY_RECEIVED →
  CLOSED, una vez resueltas todas sus incidencias). No toca inventario ni sabe de
  `InventoryMovementPoster` — igual que `Sale`/`PurchaseOrder`.
- `TransferIssue` — agregado **independiente** de `Transfer` (no una colección suya):
  referencia `transferItemId` como UUID plano. Se abre con `report(...)` y se cierra con
  `resolve(resolutionType, resolvedBy)`; nunca ejecuta la resolución (reenvío/ajuste real),
  solo la registra.
- `TransferStatusHistory` — fila de auditoría inmutable por cada transición, para sostener
  los indicadores de cumplimiento logístico que vendrán en Fase 5 (HU-36/37).

**Aplicación** (`application/port/in|out`, `domain/usecase`):
- Comandos: `CreateTransferCommand`, `ApproveTransferCommand`, `DispatchTransferCommand`,
  `ReceiveTransferCommand` (los tres últimos con listas `List<ItemQuantity(itemId,
  quantity)>` — una línea sin entrada se aprueba/despacha con su cantidad anterior por
  defecto, pero se **recibe en cero**: no hay "cantidad por defecto" razonable para lo que
  físicamente no llegó), y `ResolveTransferIssueCommand` (lleva `transferId` además de
  `issueId`, para poder cerrar la transferencia sin una consulta extra).
- `TransferSearchCriteria` — `branchId` se compara contra origen **y** destino a la vez
  (RF-46, HU-35, HU-41): una sucursal necesita ver tanto lo que pidió como lo que le están
  por enviar.
- `ManageTransferUseCase`/`QueryTransferUseCase` implementados por `TransferUseCase`;
  `ManageTransferIssueUseCase`/`QueryTransferIssueUseCase` implementados por
  `TransferIssueUseCase` (depende también de `TransferRepositoryPort`, únicamente para poder
  cerrar la transferencia dueña al resolver su última incidencia pendiente).
- `TransferUseCase.dispatchTransfer` postea `TRANSFER_OUT` en el origen por línea (RN-08,
  validado por `InventoryMovementPoster`); `receiveTransfer` postea `TRANSFER_IN` en el
  destino por lo realmente recibido (RN-09) y abre un `TransferIssue` por cada línea con
  faltante (RN-10). Cada transición también graba una `TransferStatusHistory`.

**Persistencia**: `TransferJpaEntity` (extiende `AuditableJpaEntity`) + `TransferItemJpaEntity`
(mismo patrón `@OneToMany`/`replaceItems` que `Sale`/`PurchaseOrder`) +
`TransferStatusHistoryJpaEntity` + `TransferIssueJpaEntity` (columna `transfer_item_id` plana,
no relación JPA — ver "Por qué" abajo). `TransferSpecifications.forTransfers` con el `OR`
origen/destino. `TransferPersistenceMapper` plano (no MapStruct, mismo motivo que
`PurchasingPersistenceMapper`). Tres adaptadores: `TransferPersistenceAdapter`,
`TransferStatusHistoryPersistenceAdapter`, `TransferIssuePersistenceAdapter`.

**Web**: `TransferDtos`/`TransferIssueDtos`, `TransferWebMapper` (MapStruct con métodos
`default` para comandos, igual que `SalesWebMapper`/`PurchasingWebMapper`),
`TransferController` (`/branches/{originBranchId}/transfers`, `/transfers/{id}`,
`/transfers/{id}/approval|preparation|dispatch|reception|cancellation`) y
`TransferIssueController` (`/transfers/{transferId}/issues`,
`/transfers/{transferId}/issues/{issueId}/resolution`).

**Tests**: `domain/model/TransferTest.java` (ciclo de vida completo: RN-07, aprobar con/sin
ajuste, no despachar sin preparar, recepción completa vs. parcial con faltante exacto,
cancelar solo antes de despachar, cerrar solo desde `PARTIALLY_RECEIVED`).
`domain/usecase/TransferServiceTest.java` (RN-08: propaga `InsufficientStockException` desde
el poster; postea `TRANSFER_OUT`/`TRANSFER_IN` con la sucursal y cantidad correctas; RN-10:
abre `TransferIssue` con la cantidad de faltante exacta). `domain/usecase/
TransferIssueServiceTest.java` (resolver la última incidencia pendiente cierra la
transferencia; si quedan otras pendientes, no la cierra). 24 tests nuevos, todos verdes.

## Por qué estas decisiones

- **`TransferIssue` como agregado independiente, no colección de `Transfer`**: se resuelve en
  su propio momento — normalmente por otra persona, en otro momento — y se consulta como
  bandeja propia. Igual razonamiento que `ProductPrice` frente a `PriceList`.
- **`transfer_item_id` como columna plana en `TransferIssueJpaEntity`, no `@ManyToOne`**: es
  el mismo principio que ya usa todo el repo (`SaleItem.productId`, `PurchaseOrderItem.
  productId`) — un agregado no carga otro agregado vía relación JPA. La consecuencia práctica
  es que "¿quedan incidencias sin resolver de esta transferencia?" se resuelve pasando el
  conjunto de IDs de línea (`existsUnresolvedByTransferItemIdIn`), nunca con un `JOIN` JPQL
  cruzando agregados.
- **Recepción sin cantidad por defecto**: aprobar y despachar sí tienen un valor por defecto
  razonable ("tal como se pidió/aprobó", el caso común); recibir no, porque "cuánto llegó
  realmente" no tiene un valor neutro — omitir una línea se interpreta como que no llegó nada
  de ella, no como "recibida por completo".
- **`ISSUE_PENDING` sin usar**: el `CHECK` de la migración V1 admite ese valor de `status`,
  pero el dominio nunca lo produce — una recepción con faltante deja la transferencia
  directamente en `PARTIALLY_RECEIVED` y abre la incidencia ahí mismo. Simplificación
  documentada (igual que el reenvío real tras resolver una incidencia, que tampoco crea una
  transferencia nueva): `ISSUE_PENDING` queda reservado para diferenciar, en el futuro, una
  incidencia detectada fuera de la recepción (avería reportada después) de una recepción
  simplemente incompleta.
- **Sin validación de sucursal en las sub-rutas de `/transfers/{id}/...`**: siguiendo el
  patrón ya existente en `PurchaseOrderController` (`confirmPurchaseOrder`/
  `cancelPurchaseOrder`/`receiveItem`) y `SaleController` (`confirmSale`/`cancelSale`), los
  endpoints que solo reciben el ID del documento (no un `branchId` en la ruta) no llaman a
  `CurrentUserProvider.requireCanOperateOnBranch` — es una brecha ya aceptada en el código
  existente, no algo introducido aquí.
- **Aprobar/resolver incidencia sin tocar `RoleCode`**: se reutilizan
  `RoleCode.canApproveTransfers()`/`canResolveTransferIssues()` (ya existían en el enum,
  ambos evalúan `ADMIN || BRANCH_MANAGER`) con una comprobación manual en el controlador
  (`OperationNotPermittedException` si no cumple), en vez de un segundo `hasAnyRole(...)`
  hardcodeado — la fuente de verdad de qué roles pueden aprobar/resolver queda en un solo
  sitio.
- **`carrier_id`/`route_id`/`estimated_arrival_at` sin mapear**: son nulables sin valor por
  defecto y asignar transportista es tarea de Fase 5 (Logística); mapear esas columnas ahora
  sin nada que las llene habría sido trabajo muerto. Quedan siempre `NULL` hasta que Fase 5
  las active — ver el aviso que se dejó en la sección de Fase 5 del documento maestro.

## Verificación

```
./gradlew.bat compileJava        # limpio
./gradlew.bat compileTestJava    # limpio
./gradlew.bat test --tests "*.domain.model.TransferTest" --tests "*.domain.usecase.TransferServiceTest" --tests "*.domain.usecase.TransferIssueServiceTest"
```
24 tests, `failures="0" errors="0"` en los XML de `build/test-results/test/`.

`./gradlew.bat test` (suite completa): 335 tests, 334 en verde. Única falla:
`InventoriesApplicationTests.contextLoads` (`Could not find a valid Docker environment` de
Testcontainers) — no relacionada con este trabajo, Docker no estaba corriendo al ejecutar.

## Qué falta (para la próxima sesión)

Fase 4 queda cerrada del todo. Lo siguiente en el plan, en orden:

- **Fase 5 — Logística** (NO INICIADO): `Carrier`/`LogisticsRoute` CRUD, más terminar de
  activar `carrier_id`/`route_id`/`estimated_arrival_at` en `Transfer` (ver nota dejada en el
  documento maestro, sección Fase 5) y la consulta de cumplimiento por ruta (HU-36/37).
- Fase 6 (Dashboard) y Fase 7 (deuda técnica de cierre) siguen después, en ese orden.

Para retomar: leer `docs/PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS.md` completo (decisiones de
diseño vigentes y errores ya resueltos) y empezar directo por la Fase 5.
