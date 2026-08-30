# Sistema de Inventario Multi-Sucursal — Backend

> Prueba técnica para OptiPlant Consultores. Backend de un sistema de gestión de
> inventario para una organización con múltiples sucursales: cada sucursal opera con
> autonomía sobre sus transacciones locales, pero comparte visibilidad de inventario en
> tiempo real con el resto de la red.
>
> **Principio rector del proyecto**: cada decisión de diseño debe poder responder
> *"¿por qué se hizo así?"*. Este documento existe para dejar esa respuesta por escrito,
> no solo el qué.

---

## Tabla de contenidos

1. [Estado del proyecto](#1-estado-del-proyecto)
2. [Arquitectura](#2-arquitectura)
3. [Stack tecnológico y justificación](#3-stack-tecnológico-y-justificación)
4. [Modelo de datos](#4-modelo-de-datos)
5. [Seguridad](#5-seguridad)
6. [Módulos funcionales](#6-módulos-funcionales)
7. [Decisiones técnicas transversales](#7-decisiones-técnicas-transversales)
8. [Pruebas](#8-pruebas)
9. [Cómo ejecutar](#9-cómo-ejecutar)
10. [Documentación de la API](#10-documentación-de-la-api)
11. [Deuda técnica y decisiones conscientes de alcance](#11-deuda-técnica-y-decisiones-conscientes-de-alcance)
12. [Uso de inteligencia artificial en el desarrollo](#12-uso-de-inteligencia-artificial-en-el-desarrollo)
13. [Pendiente](#13-pendiente)
14. [Historial de documentos de diseño](#14-historial-de-documentos-de-diseño)

---

## 1. Estado del proyecto

El **backend está funcionalmente completo**: los 6 módulos obligatorios del enunciado,
más la funcionalidad adicional, más el cierre de deuda técnica de seguridad. **445 tests,
445 en verde**, incluyendo el arranque completo del contexto Spring contra PostgreSQL real.

| Bloque | Estado |
|---|---|
| Arquitectura hexagonal + esquema físico (26 tablas) | ✅ Completo |
| Seguridad (JWT, autorización por rol y ámbito) | ✅ Completo |
| Catálogo (`Category`, `Product`, `UnitOfMeasure`, `ProductUnit`) | ✅ Completo |
| Inventario y movimientos (núcleo del dominio) | ✅ Completo |
| Compras (`Supplier`, `PurchaseOrder`) | ✅ Completo |
| Ventas (`Sale`, `PriceList`, `ProductPrice`) | ✅ Completo |
| Transferencias entre sucursales (`Transfer`, 5 estados) | ✅ Completo |
| Logística (`Carrier`, `LogisticsRoute`, cumplimiento por ruta) | ✅ Completo |
| Dashboard analítico (solo lectura) | ✅ Completo |
| Funcionalidad adicional: alertas de stock (`InventoryAlert`) | ✅ Completo |
| Deuda técnica: `MockMvc` con JWT real, bugs de arranque | ✅ Completo |
| Siembra del administrador inicial (`AdminBootstrapUseCase`) | ✅ Completo |
| Frontend | ❌ No iniciado (fuera del alcance actual, ver [§13](#13-pendiente)) |
| Docker Compose (`docker compose up` end-to-end) | ⚠️ Por confirmar/crear |
| Diagramas de ingeniería (casos de uso, actividades, arquitectura, E-R) | ❌ No generados |

---

## 2. Arquitectura

### 2.1 Hexagonal por capas

```text
io.github.KevinMitsi.inventories
│
├── domain/                        ← El centro. No depende de NADA.
│   ├── model/                     Modelos de dominio, objetos de valor, enums
│   ├── usecase/                   Implementación directa de los casos de uso
│   └── exception/                 Fallos de invariante del propio modelo
│
├── application/                   ← Orquestación. Depende solo de domain.
│   ├── port/in/                   Casos de uso (interfaces) + command/ + query/
│   ├── port/out/                  Contratos de persistencia y servicios externos
│   ├── service/                   Wrapper @Transactional que delega en domain.usecase
│   └── exception/                 Fallos de orquestación
│
└── infrastructure/                ← Detalles. Depende de application y domain.
    ├── adapter/
    │   ├── web/                   Controladores REST, DTO, mapeadores, manejo de errores
    │   ├── persistence/           Entidades JPA, repositorios, mapeadores, adaptadores
    │   └── security/               JWT, filtro de autenticación
    └── config/                    Cableado de Spring, OpenAPI, seguridad
```

**La regla que sostiene todo**: las dependencias apuntan siempre hacia adentro. `domain`
no importa nada de `application` ni de `infrastructure`; `application` no importa nada de
`infrastructure`. La inversión se logra con puertos: la aplicación **declara** lo que
necesita (`BranchRepositoryPort`) y la infraestructura lo **satisface**
(`BranchPersistenceAdapter`). Verificable mecánicamente:

```bash
grep -rn "import io.github.KevinMitsi.inventories.\(application\|infrastructure\)" \
     src/main/java/io/github/KevinMitsi/inventories/domain/
grep -rn "import io.github.KevinMitsi.inventories.infrastructure" \
     src/main/java/io/github/KevinMitsi/inventories/application/
```
Ninguno debe devolver resultados.

**Por qué pagar el coste de una capa extra** (modelo de dominio + entidad JPA + mapeador,
DTO + comando, por cada agregado):

- Las reglas de negocio se prueban **sin infraestructura**: sin Docker, sin contexto de
  Spring. Cuando la regla es *"no vender por encima del stock"* o *"recalcular el costo
  promedio ponderado"*, poder ejercitarla en milisegundos es la diferencia entre probarla
  a fondo o no probarla.
- El contrato público (API) y el modelo interno evolucionan por separado.
- JPA no dicta el diseño del dominio: sin la separación, el modelo acabaría con
  constructor vacío, campos no finales y carga perezosa filtrándose a las reglas de
  negocio.

### 2.2 Nota sobre `domain.usecase` vs `application.service`

A partir de la Fase 3, los casos de uso se implementan directamente en
`domain.usecase.*UseCase` (p. ej. `SaleUseCase`, `TransferUseCase`), y
`application.service.*Service` es un wrapper `@Transactional` que delega 1:1. Ambos
implementan las mismas interfaces `port.in`. Esto tuvo una consecuencia real de arranque
(ver [§11](#11-deuda-técnica-y-decisiones-conscientes-de-alcance)): sin `@Primary` en el
`@Service`, Spring encuentra dos candidatos para cada interfaz y falla con
`NoUniqueBeanDefinitionException` en el primer arranque contra un contexto real. Fix
aplicado: **todo `@Service` lleva `@Primary`**.

### 2.3 Corte vertical de referencia

El primer agregado construido de punta a punta (sucursales) fija el patrón que se repite
en todos los demás:

```text
Controller          valida formato (Jakarta), sin lógica de negocio
  └─ WebMapper       Request + parámetros de ruta → Command
      └─ UseCase (interfaz)
          └─ Service/UseCaseImpl   valida reglas de orquestación → transacción
              ├─ RepositoryPort (interfaz) de otros agregados, para validar referencias
              ├─ Agregado.create()          invariantes propios del modelo
              └─ RepositoryPort (interfaz)
                  └─ PersistenceAdapter
                      ├─ PersistenceMapper   Dominio ↔ JpaEntity
                      └─ JpaRepository       Spring Data
  └─ WebMapper       Dominio → Response
```

---

## 3. Stack tecnológico y justificación

| Elección | Motivo |
|---|---|
| **Java 21 + Spring Boot 4.1** | Hilos virtuales (`spring.threads.virtual.enabled`) para I/O de base de datos sin el coste de un modelo reactivo completo; ecosistema maduro para arquitectura hexagonal, seguridad y persistencia. |
| **PostgreSQL** | Relacional: el dominio tiene invariantes fuertes (stock nunca negativo, coherencia de estados, unicidad por organización) que se apoyan en `CHECK`, índices únicos/parciales y claves foráneas — encaja mejor que un modelo NoSQL sin esquema. |
| **Flyway** | Un único esquema físico (`V1__baseline_schema.sql`, 26 tablas) versionado y reproducible; `ddl-auto: validate` hace que Hibernate solo compruebe, nunca gobierne el esquema. |
| **JWT (JJWT) + BCrypt** | Autenticación sin estado (escalable horizontalmente, RNF-08), aislados detrás de puertos de salida (`TokenProviderPort`, `PasswordHasherPort`) para que `application` no dependa de ninguna librería concreta. |
| **MapStruct** | Mapeo generado en compilación (`unmappedTargetPolicy = ERROR`): un campo sin correspondencia rompe el build, no aparece como `null` en producción. |
| **Testcontainers** | Pruebas de integración contra PostgreSQL real, no una base en memoria que oculte diferencias de dialecto SQL. |

---

## 4. Modelo de datos

Esquema físico completo en `V1__baseline_schema.sql` — **una sola migración**, sin
necesidad de migraciones adicionales durante todo el desarrollo. 26 tablas normalizadas
a 3FN, agrupadas por dominio:

| Dominio | Tablas principales |
|---|---|
| Organización | `organization`, `branch` |
| Seguridad | `app_role`, `app_user` (`user`/`role` son palabras reservadas en PostgreSQL) |
| Catálogo | `category`, `product`, `unit_of_measure`, `product_unit` |
| Inventario | `inventory`, `inventory_movement_type` (enum+CHECK), `inventory_movement`, `inventory_adjustment`(+`_item`), `inventory_alert_type`, `inventory_alert` |
| Proveedores y compras | `supplier`, `purchase_order`(+`_item`), `purchase_order_status` |
| Ventas | `sale`(+`_item`), `sale_status`, `price_list`, `product_price` |
| Transferencias | `transfer`(+`_item`), `transfer_status`, `transfer_priority`, `transfer_status_history`, `transfer_issue`(+ tipo y resolución) |
| Logística | `carrier`, `logistics_route` |

### Decisiones de modelado clave

- **Catálogos de estado híbridos**: estados de venta/compra/transferencia, tipo de
  movimiento, prioridad, tipo/resolución de incidencia, tipo de alerta → **enum + `CHECK`**
  (transiciones ya viven en el código, una tabla añadiría un JOIN y una segunda fuente de
  verdad). `unit_of_measure`, `carrier`, `logistics_route`, `price_list`, `product_price`
  → **tabla real** (el negocio los extiende en ejecución sin desplegar código).
- **Entre agregados se referencia por identificador (UUID), nunca por `@ManyToOne`**: por
  ejemplo `BranchJpaEntity.organizationId` es un `UUID` plano. Esto hace que el N+1 entre
  agregados sea **imposible por construcción** y deja el límite del agregado explícito.
  `@ManyToOne`/`@EntityGraph` sí se usan *dentro* de un agregado (una venta y sus líneas).
- **`inventory_movement` usa FKs específicas** (`purchase_order_id`, `sale_id`,
  `transfer_id`, `adjustment_id`) en lugar de una pareja polimórfica genérica
  (`reference_type`/`reference_id`), con un `CHECK` que garantiza como mucho una no nula —
  prioriza integridad referencial real sobre flexibilidad.
- **Índices parciales** donde aportan: unidad base única por producto activo, alerta
  abierta única por (inventario, tipo), inventario bajo mínimo, código de barras único
  solo cuando existe.
- **Objetos de valor con escala fija**: `Quantity` (6 decimales), `Money` (4 decimales),
  `Percentage` (0–100, 2 decimales). En un sistema cuyo invariante es que el saldo cuadre
  con sus movimientos, el error acumulado de punto flotante sería indetectable y
  corrompería el histórico. `BigDecimal` puro no basta —
  `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` es `false`— así que la escala se
  normaliza en el constructor del objeto de valor.

---

## 5. Seguridad

### 5.1 Aislamiento de librerías concretas

| Puerto | Implementación | Qué aísla |
|---|---|---|
| `PasswordHasherPort` | `BCryptPasswordHasherAdapter` | El algoritmo de hash |
| `TokenProviderPort` | `JwtTokenProviderAdapter` | El formato JWT y la biblioteca |

`AuthenticationService` pide *"cifra esto"* y *"dame un token para este usuario"* — no
sabe que existe BCrypt ni que el token es JWT. Verificable:
```bash
grep -rEi "jsonwebtoken|bcrypt|jakarta.persistence|hibernate" \
     src/main/java/io/github/KevinMitsi/inventories/application/
```

### 5.2 Dos tokens con propósitos separados

| Token | Vida | Autoriza operaciones |
|---|---|---|
| `accessToken` | 1 hora | Sí |
| `refreshToken` | 7 días | No — solo obtiene uno de acceso nuevo |

El token de acceso lleva rol, sucursal y organización, para resolver autorización **sin
consultar la base en cada petición** (RNF-08). El precio: es una fotografía del momento de
emisión — si a un usuario se le cambia el rol o se le da de baja, su token de acceso sigue
siendo válido hasta caducar (máximo 1 hora). La renovación sí recarga el usuario desde la
base, así que una baja se vuelve efectiva en la siguiente renovación.

### 5.3 Autorización en dos niveles

| Nivel | Herramienta | Resuelve |
|---|---|---|
| Rol | `@PreAuthorize("hasRole('ADMIN')")` | "Esto lo hace un administrador" |
| Datos | `CurrentUserProvider` (`requireBelongsToOrganization`, `requireCanOperateOnBranch`, `requireSelfOrAdmin`...) | "¿Es *tu* sucursal? ¿*Tu* organización?" |

El segundo nivel no puede resolverse con anotaciones: exige comparar la sucursal/
organización del recurso cargado con la del solicitante. Ejemplo: en la creación de
usuarios, `@PreAuthorize` comprueba que sea `ADMIN`, pero `requireBelongsToOrganization`
es lo que impide que ese administrador cree usuarios en **otra** organización cambiando el
UUID de la ruta.

### 5.4 Otras decisiones de seguridad relevantes

- **Los tres fallos de login son indistinguibles** (correo inexistente, contraseña
  incorrecta, cuenta dada de baja) — distinguirlos permitiría enumerar direcciones
  registradas.
- **Defensa por tiempo de respuesta**: si el correo no existe, el servicio igual verifica
  contra un hash de referencia y descarta el resultado, para que un correo desconocido no
  responda en microsegundos frente a los milisegundos deliberadamente lentos de BCrypt.
- **Contraseñas nunca en un `toString`**: todos los DTOs/comandos/entidades que las tocan
  redefinen `toString` para enmascararlas.
- **El administrador inicial se crea en código, no en una migración** — una contraseña en
  un fichero versionado es una contraseña pública. Solo se crea si no existe ninguno
  (reiniciar no restablece una contraseña ya cambiada).
- **Cierre por omisión**: `anyRequest().authenticated()`. Un endpoint nuevo nace protegido.
- **Invariante del último administrador**: la organización nunca se queda sin un
  administrador activo — se cierran los dos caminos que llevarían a ello (dar de baja y
  degradar de rol).

---

## 6. Módulos funcionales

### 6.1 Catálogo (`Category`, `Product`, `UnitOfMeasure`, `ProductUnit`)

`Product` es agregado raíz de sus `ProductUnit` (presentaciones), porque existe un
invariante que ninguna restricción de columna puede expresar — *siempre hay exactamente
una unidad base activa*. Cambiar la unidad base exige el factor de conversión de la
anterior explícito (nunca calculado automáticamente, para no introducir un redondeo
periódico sistemático).

**Trampa técnica resuelta**: paginar un agregado con colección (`Product` + sus
`ProductUnit`) es incompatible con `JOIN FETCH` — la unión multiplica filas y Hibernate
resolvería la página en memoria, agotando el servidor con un catálogo grande. Solución por
caso de uso:

| Consulta | Estrategia | Consultas emitidas |
|---|---|---|
| `findById`/`findBySku` (un producto) | `@EntityGraph` | 1 |
| Listado paginado | `@BatchSize(50)` en la colección | 2 fijas |

### 6.2 Inventario y movimientos (núcleo del dominio, RN-04)

*El stock nunca cambia sin un movimiento que lo explique.*

- **`InventoryMovementPoster`** — único punto interno que toca `inventory.quantity`.
  Bloqueo pesimista por `(branch_id, product_id)`, valida no-negativo, aplica costo
  promedio ponderado solo en `PURCHASE_IN`, gestiona alertas. Compras, ventas,
  transferencias y ajustes son **clientes** de esto — nunca tocan `inventory`
  directamente.
- Conversión de unidades al postear vía `ProductUnit.conversionFactor`: el stock siempre
  se guarda en unidad base.
- `InventoryAdjustment` (documento formal con líneas + aprobación) es distinto de un
  movimiento manual directo (sin cabecera).

### 6.3 Compras (`Supplier`, `PurchaseOrder`)

Ciclo completo de orden a proveedor: creación, condiciones (precio, descuentos, plazo),
recepción (que actualiza inventario automáticamente vía el poster), histórico por
proveedor y por producto. Costo promedio ponderado recalculado en cada `PURCHASE_IN`.

### 6.4 Ventas (`Sale`, `PriceList`, `ProductPrice`)

- `Sale` nace `DRAFT`, valida stock disponible al confirmar (RN-03, delegado en
  `InventoryMovementPoster`, que propaga `InsufficientStockException`), postea
  `SALE_OUT` por línea al confirmar.
- Cancelar una venta `CONFIRMED` postea `RETURN_IN` compensatorio por línea; cancelar una
  `DRAFT` no postea ningún movimiento.
- `PriceList` cuelga de `organizationId` (aplica a varias sucursales); `Sale` cuelga de
  `branchId` (ocurre en una sucursal concreta, RN-02) — cada uno usa el nivel de
  autorización correspondiente (`requireBelongsToOrganization` vs.
  `requireCanOperateOnBranch`).

### 6.5 Transferencias entre sucursales (`Transfer`)

Máquina de estados de 5 pasos en el dominio:

```text
REQUESTED → APPROVED → IN_PREPARATION → IN_TRANSIT → RECEIVED | PARTIALLY_RECEIVED
```
más `cancel()` (solo antes de despachar) y `close()` (desde `PARTIALLY_RECEIVED`, una vez
resueltas todas sus incidencias).

- Cada línea (`TransferItem`) encadena 4 cantidades: `requested ≥ approved ≥ shipped ≥
  received`, cada una fijada por su propio método.
- `TransferIssue` es un **agregado independiente**, no una colección de `Transfer`: se
  resuelve en su propio momento, normalmente por otra persona, y se consulta como bandeja
  propia — mismo criterio que `ProductPrice` frente a `PriceList`.
- **Recepción sin cantidad por defecto**: aprobar/despachar sí tienen un valor por defecto
  razonable ("tal como se pidió"); recibir no, porque omitir una línea se interpreta como
  que no llegó nada de ella.
- `dispatchTransfer` postea `TRANSFER_OUT` en origen (RN-08); `receiveTransfer` postea
  `TRANSFER_IN` en destino por lo realmente recibido (RN-09) y abre un `TransferIssue` por
  cada línea con faltante (RN-10).
- `TransferStatusHistory` registra cada transición para sostener los indicadores de
  cumplimiento logístico.

### 6.6 Logística (`Carrier`, `LogisticsRoute`)

- `Transfer.assignLogistics(carrierId, routeId, estimatedArrivalAt)`, válido solo antes de
  despachar. La validación de que "la ruta elegida corresponde al trayecto real" vive en
  el *use case* (necesita ambas entidades cargadas), no en `Transfer`, que no conoce
  `LogisticsRoute` (mismo principio de no cargar un agregado desde otro).
- **Cumplimiento por ruta (HU-36/37)**: consulta **nativa** (no JPQL) que agrega
  `transfer` sobre `logistics_route`, comparando duración real (`received_at - shipped_at`)
  contra la estimada. `onTimeRate`/`averageActualMinutes` son `Double` **nulable**, no
  `0.0` — una ruta sin transferencias completadas tiene "sin datos", no "0% de
  cumplimiento", y un cliente necesita poder distinguirlo.

### 6.7 Dashboard analítico (solo lectura)

Sin agregado de dominio nuevo — tres proyecciones de solo lectura:

| Proyección | Cubre |
|---|---|
| `SalesSummary` | Ventas confirmadas por mes y sucursal (mes actual vs. anteriores) |
| `ProductRotation` | Cantidad vendida por producto en un período, de mayor a menor demanda; productos sin ventas aparecen al final con cantidad cero |
| `BranchComparison` | Ventas de últimos 30 días + valor de inventario a costo promedio + productos bajo stock mínimo, por sucursal — **reservado a `ADMIN`** |

Todas resueltas con `@Query` nativas sobre tablas ya existentes (sin migración nueva), con
los mismos índices que ya sostenían `inventory_alert`.

### 6.8 Funcionalidad adicional: alertas de stock (`InventoryAlert`)

Elegida entre las funcionalidades adicionales propuestas por el enunciado (alertas
inteligentes, predicción de demanda, gestión avanzada de proveedores, control de
caducidad, auditoría, reportes exportables). **Por qué esta y no otra**: es la de mayor
apalancamiento sobre el núcleo ya construido (`InventoryMovementPoster` ya gestiona el
ciclo de vida de las alertas al postear cada movimiento), sin requerir un modelo
estadístico (predicción de demanda) ni un dominio nuevo con campos y reglas adicionales
(caducidad). Índice parcial (`ix_inventory_low_stock`) + alerta abierta única por
(inventario, tipo) para no duplicar avisos.

---

## 7. Decisiones técnicas transversales

| Técnica | Dónde se aplica | Por qué ahí |
|---|---|---|
| Referencia por identificador | Entre agregados | El N+1 deja de ser posible por construcción |
| `@EntityGraph` | Asociación a-uno, o a-muchos sin paginar | Una consulta con la unión resuelta |
| `@BatchSize` | Colección **paginada** | Evita que Hibernate pagine en memoria |
| Enum + `CHECK` | Catálogos de estado cerrados, con transiciones en código | Evita JOIN y segunda fuente de verdad |
| Tabla real | Catálogos que el negocio extiende en ejecución | No requiere desplegar código para crecer |
| `PageQuery`/`PageResult` propios | Todos los puertos de salida | Cambiar de tecnología de persistencia no obliga a tocar contratos; techo de 100 elementos por página (RNF-07) |
| Objetos de valor (`Money`, `Quantity`, `Percentage`) | Todo el dominio | Escala fija en el constructor, sin error acumulado de punto flotante |
| MapStruct con `unmappedTargetPolicy = ERROR` | Web mappers | Una propiedad sin mapear rompe el build, no aparece `null` en producción |
| Mapeadores de persistencia como `@Component` plano (no MapStruct) | Entidad ↔ Dominio | La reconstitución revalida invariantes vía `reconstitute(...)`; un `@Mapper` con solo métodos `default` puede no generar bean Spring |
| Lista blanca de campos de ordenación | `PageQueryTranslator` | Un nombre de campo llegado de la petición no puede acabar directo en una consulta |

### El dominio no conoce HTTP

Cada excepción de dominio lleva un `DomainErrorCode`. `ApiExceptionHandler`, en el
adaptador web, es el único punto que traduce ese código a un estado HTTP
(`RESOURCE_NOT_FOUND`→404, `DUPLICATE_RESOURCE`/`CONCURRENT_MODIFICATION`→409,
`OPERATION_NOT_PERMITTED`→403, el resto de validaciones de negocio→422). Si el dominio
devolviera un 404 directamente, exponerlo mañana por otro transporte obligaría a
reescribirlo.

### Manejo de errores — tres garantías de seguridad (RNF-03)

1. Un fallo no previsto nunca filtra detalles internos (traza completa en servidor,
   mensaje genérico + `traceId` al cliente).
2. Un campo sensible (contraseña, token) nunca vuelve en la respuesta, ni siquiera cuando
   falla su validación.
3. El mensaje del driver de base de datos nunca sale al cliente (revela nombres de
   tablas/columnas/restricciones).

---

## 8. Pruebas

**445 tests, 445 en verde**, incluyendo por primera vez `InventoriesApplicationTests.
contextLoads` pasando de verdad contra PostgreSQL real (no solo "sin fallar por falta de
Docker").

| Capa | Qué se prueba | Ejemplo |
|---|---|---|
| Dominio | Invariantes del modelo, sin contexto ni mocks | `ProductTest`, `TransferTest`, `SaleTest` |
| Casos de uso (`domain.usecase`) | Orquestación: qué se valida, en qué orden, qué se deja de hacer si algo falla | `TransferServiceTest`, `SaleServiceTest` |
| Infraestructura | Adaptadores de persistencia, JWT, hash, traducción de paginación | `JwtTokenProviderAdapterTest`, `PageQueryTranslatorTest` |
| Web (`MockMvc` + JWT real) | Cadena de seguridad completa: rol + ámbito + 401/403/404/400 | `UserControllerTest`, `DashboardControllerTest` |

### Criterio sobre qué se sustituye por un doble (mock)

No es uniforme:

| Componente | ¿Doble? | Motivo |
|---|---|---|
| Repositorios de Spring Data | Sí | La responsabilidad del adaptador es traducir, no consultar |
| Mapeadores de MapStruct | No | Se comprueba la correspondencia real entre campos |
| `JwtTokenProviderAdapter` | No | Se comprueba que la firma se verifique y un token alterado se rechace |
| `BCryptPasswordHasherAdapter` | No | Se comprueba que dos cifrados de la misma clave difieran (propiedad real del algoritmo) |

### `MockMvcTestSupport`: JWT real, nunca `@WithMockUser`

Requisito explícito: "ejercitar la cadena de seguridad completa". Base
`@SpringBootTest(webEnvironment = MOCK) @AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class) @Transactional`, con helpers para crear
organización/sucursal/usuarios de cada rol y emitir un JWT real vía `TokenProviderPort`.
Cada test corre en su propia transacción revertida al final.

---

## 9. Cómo ejecutar

```bash
docker compose up -d      # levanta PostgreSQL (y demás dependencias)
./gradlew bootRun
```

Al arrancar por primera vez, `AdminBootstrapRunner` (`ApplicationRunner`, ver
[§5.4](#54-otras-decisiones-de-seguridad-relevantes)) siembra la organización por defecto
(`OPTIPLANT`) y el administrador inicial si todavía no existen — variables de entorno
`JWT_SECRET`, `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` — **definir las tres
antes de exponer esto fuera de un entorno local**, los valores por defecto
(`admin@admin.com` / `admin123`) son públicos en este repositorio. La contraseña se cifra
con el mismo `PasswordHasherPort` (BCrypt) que usa el resto del sistema — nunca se guarda
ni se siembra un hash a mano — y reiniciar la aplicación no la restablece si ya se cambió
desde la API.

```bash
# 1. Autenticarse
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@admin.com","password":"admin123"}'

# 2. Usar el token
TOKEN="<accessToken de la respuesta>"
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
```

> ⚠️ **Pendiente de confirmar**: verificar que exista un `docker-compose.yml` que levante
> **toda** la solución (backend + base de datos, y frontend cuando exista) con un solo
> comando, tal como exige el enunciado "sin excepción". Hasta ahora el proyecto solo usa
> Testcontainers para pruebas de integración, no necesariamente un compose de ejecución.

```bash
./gradlew build     # compila, corre los 440 tests, genera OpenAPI
```

---

## 10. Documentación de la API

Generada automáticamente de las anotaciones (documentación y código no pueden divergir):

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

El esquema de seguridad `bearerAuth` se declara como **requisito global**: los endpoints
son privados salvo indicación contraria explícita — olvidar una anotación no deja un
recurso documentado erróneamente como abierto.

### Resumen de endpoints por módulo

| Módulo | Rutas base |
|---|---|
| Auth | `POST /auth/login`, `POST /auth/refresh`, `GET /auth/me` (públicos los dos primeros) |
| Usuarios | `/organizations/{id}/users`, `/users/{id}`, `/users/{id}/profile`\|`assignment`\|`password`\|`deactivation`\|`activation` |
| Sucursales | `/organizations/{id}/branches`, `/branches/{id}` |
| Catálogo | `/organizations/{id}/categories`\|`products`, `/categories/{id}`, `/products/{id}`, `/products/{id}/units`, `/units-of-measure` |
| Inventario | `/inventory`, `/inventory-adjustments`, `/inventory-alerts` |
| Compras | `/organizations/{id}/suppliers`, `/branches/{id}/purchase-orders`, `/purchase-orders/{id}` |
| Ventas | `/organizations/{id}/price-lists`, `/price-lists/{id}`, `/branches/{id}/sales`, `/sales/{id}` |
| Transferencias | `/branches/{originBranchId}/transfers`, `/transfers/{id}`, `/transfers/{id}/approval`\|`preparation`\|`dispatch`\|`reception`\|`cancellation`\|`logistics-assignment`, `/transfers/{id}/issues` |
| Logística | `/organizations/{id}/carriers`, `/organizations/{id}/logistics-routes`, `/logistics-routes/compliance` |
| Dashboard | `/organizations/{id}/dashboard/sales-summary`\|`product-rotation`\|`branch-comparison` (este último solo `ADMIN`) |

Ver Swagger para el detalle completo de parámetros, roles requeridos y esquemas de
respuesta de cada uno.

---

## 11. Deuda técnica y decisiones conscientes de alcance

### Bugs de arranque encontrados y corregidos

Ningún test había levantado antes un contexto Spring completo contra Postgres real —
`contextLoads` llevaba fases enteras fallando "por Docker" sin que nadie confirmara que,
con Docker disponible, el contexto realmente cargaba. Al forzarlo, aparecieron tres
problemas reales:

1. **`country_code` no pasaba la validación de esquema de Hibernate** — `CHAR(2)` en la
   migración necesita `@JdbcTypeCode(SqlTypes.CHAR)` explícito en Hibernate 6+.
2. **Migración a Spring Boot 4.1 / Jackson 3 incompleta** — faltaba
   `spring-boot-starter-webmvc-test`, `SecurityErrorResponder` importaba
   `com.fasterxml.jackson` en vez de `tools.jackson`, y
   `write-dates-as-timestamps` seguía en la ruta de propiedad antigua.
3. **Ambigüedad de bean sistémica** entre cada `@Service` y su `@Bean` crudo — resuelto con
   `@Primary` en los 18 servicios (ver [§2.2](#22-nota-sobre-domainusecase-vs-applicationservice)).

### Descartado conscientemente

- **Autoría de Spring REST Docs (`.adoc`)**: la dependencia está preparada, pero no hay
  fuente `.adoc` todavía — `./gradlew build` incluye `asciidoctor` con resultado
  `NO-SOURCE`.
- **`MockMvc` para los controladores de fases 1-5 no listados explícitamente en el plan de
  cierre** (`InventoryController`, `PurchaseOrderController`, `SaleController`,
  `TransferController`, etc.): quedan cubiertos por tests de `domain.usecase`/
  `application.service`, no por `MockMvc` — ampliar esa cobertura es trabajo futuro
  opcional, no bloqueante.
- **`ISSUE_PENDING`** (valor de estado admitido por el `CHECK` de la migración) nunca se
  produce desde el dominio — una recepción con faltante deja la transferencia
  directamente en `PARTIALLY_RECEIVED`. Reservado para diferenciar, en el futuro, una
  incidencia detectada fuera de la recepción.
- **Reenvío real tras resolver una incidencia de transferencia**: fuera de alcance MVP,
  simplificación documentada desde el plan original.

---

## 12. Uso de inteligencia artificial en el desarrollo

> ⚠️ Sección pendiente de completar con evidencia concreta (capturas/fragmentos de
> prompts) antes de la entrega — lo que sigue resume, a partir de la documentación de
> cierre de cada fase, en qué se apoyó el trabajo asistido por IA.

- **Diseño de arquitectura y documentación de decisiones**: cada fase se cerró con un
  documento de auditoría (`PHASE*.md`) que registra qué se construyó, cómo y por qué,
  incluyendo alternativas descartadas y su justificación — este mismo README es una
  síntesis de esos documentos.
- **Generación de código**: módulos con patrones repetitivos (CRUD de agregados,
  mapeadores web/persistencia, controladores REST) siguiendo la plantilla fijada por el
  primer corte vertical (sucursales).
- **Generación de tests**: cobertura de dominio, casos de uso e infraestructura para cada
  fase nueva, incluyendo los tests `MockMvc` con JWT real de la fase de deuda técnica.
- **Revisión de código**: detección de los tres bugs de arranque del §11, nunca
  observados hasta que se forzó un arranque de contexto real contra Postgres.
- **Consulta de buenas prácticas**: decisiones como el tratamiento de N+1 (referencia por
  identificador / `@EntityGraph` / `@BatchSize` según el caso), o la migración a
  Spring Boot 4.1 / Jackson 3.

**Pendiente**: estimación del % de código generado con asistencia de IA, y ejemplos
concretos de prompts usados, para dejar la evidencia que pide el punto 9.2 del enunciado.

---

## 13. Pendiente

Lo que falta para el entregable completo, en orden de bloqueo respecto al enunciado:

- [ ] **Docker Compose** end-to-end (`docker compose up` sin configuración manual) —
  requisito "sin excepción" del enunciado.
- [ ] **Diagramas de ingeniería**: casos de uso, actividades (transferencia y venta como
  mínimo), arquitectura, entidad-relación. El modelo E-R y la arquitectura ya están
  descritos en detalle en texto (`ENTITIES.md`, `PHASE2-ARQUITECTURA-BACKEND.md`);
  falta convertirlos en diagrama (draw.io/Mermaid/PlantUML).
- [ ] **Frontend** — no iniciado. El enunciado exige separación de capas con frontend
  consumiendo la API sin lógica de negocio propia.
- [ ] **Sección de IA** con evidencia concreta (capturas de prompts, % estimado).
- [ ] (Opcional, no bloqueante) `MockMvc` para los controladores de fases 1-5 restantes.
- [ ] (Opcional, no bloqueante) Autoría de Spring REST Docs (`.adoc`).

---

## 14. Historial de documentos de diseño

Este README sintetiza los siguientes documentos de auditoría, generados a medida que se
cerraba cada fase (se conservan en el repositorio como rastro auditable del proceso):

| Documento | Contenido |
|---|---|
| `PHASE1.md` | Requisitos, reglas de negocio, historias de usuario |
| `ENTITIES.md` | Modelo E-R textual, normalizado a 3FN |
| `PHASE2-ARQUITECTURA-BACKEND.md` | Arquitectura hexagonal, esquema físico, corte vertical de referencia |
| `PHASE3-SEGURIDAD.md` | JWT, autorización por rol y ámbito, invariante del último administrador |
| `PHASE4-CATALOGO.md` | Catálogo de productos, paginación de agregados con colección |
| `PHASE5-INVENTARIO-VENTAS-TRANSFERENCIAS-V5/V6/V7.md` | Plan maestro de inventario, compras, ventas, transferencias, logística y dashboard, con snapshots versionados por fase cerrada |
| `PHASE5.3-VENTAS-CIERRE.md` | Cierre de ventas y listas de precio |
| `PHASE5.4-TRANSFERENCIAS-CIERRE.md` | Cierre de transferencias entre sucursales |
| `PHASE5.5-LOGISTICA-CIERRE.md` | Cierre de logística y cumplimiento de rutas |
| `PHASE5.6-DASHBOARD-CIERRE.md` | Cierre de dashboard analítico |
| `PHASE5.7-DEUDA-TECNICA-CIERRE.md` | Cierre de deuda técnica: bugs de arranque, `MockMvc` con JWT real |