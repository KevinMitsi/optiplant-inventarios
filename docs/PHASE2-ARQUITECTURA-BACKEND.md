# FASE 2 — Arquitectura del backend y primer corte vertical

> Documento de auditoría. Registra **qué** se construyó, **cómo** y, sobre todo, **por qué**
> se decidió así. Toda decisión reflejada aquí puede contrastarse con `PHASE1.md`
> (requisitos y reglas de negocio) y `ENTITIES.md` (modelo de datos).

---

## 1. Alcance de esta fase

Al cerrar la Fase 1 el repositorio solo contenía el esqueleto de Spring Initializr y la
documentación de diseño. Esta fase entrega:

1. La **arquitectura completa** del backend, con sus reglas de dependencia.
2. La **configuración de infraestructura**: base de datos, pool de conexiones, agrupación
   de sentencias, transacciones, documentación de API.
3. El **esquema físico completo** (26 tablas) como migración de Flyway.
4. El **núcleo compartido**: excepciones, objetos de valor, paginación, manejo global de
   errores.
5. Un **corte vertical completo y funcionando** —la gestión de sucursales— que sirve de
   plantilla para el resto de agregados.

Lo que **no** entra todavía: autenticación real, y los agregados de catálogo, inventario,
compras, ventas, transferencias, logística y analítica. El apartado 9 detalla el estado.

---

## 2. Arquitectura: hexagonal por capas

### 2.1 Estructura

```text
io.github.KevinMitsi.inventories
│
├── domain/                        ← El centro. No depende de NADA.
│   ├── model/                     Modelos de dominio, objetos de valor, enums
│   └── exception/                 Fallos de invariante del propio modelo
│
├── application/                   ← Orquestación. Depende solo de domain.
│   ├── port/in/                   Casos de uso (interfaces) + command/ + query/
│   ├── port/out/                  Contratos de persistencia y servicios externos
│   ├── service/                   Implementación de los casos de uso
│   └── exception/                 Fallos de orquestación
│
└── infrastructure/                ← Detalles. Depende de application y domain.
    ├── adapter/
    │   ├── web/                   Controladores REST, DTO, mapeadores, manejo de errores
    │   ├── persistence/           Entidades JPA, repositorios, mapeadores, adaptadores
    │   └── security/              JWT, filtros (pendiente)
    └── config/                    Cableado de Spring, OpenAPI, seguridad
```

### 2.2 La regla que sostiene todo

**Las dependencias apuntan siempre hacia adentro.** `domain` no importa nada de
`application` ni de `infrastructure`; `application` no importa nada de `infrastructure`.

La inversión se consigue con los puertos: la capa de aplicación **declara** lo que necesita
(`BranchRepositoryPort`) y la infraestructura lo **satisface** (`BranchPersistenceAdapter`).
El servicio nunca conoce Spring Data.

Esto se puede verificar mecánicamente:

```bash
# No debe devolver ninguna línea
grep -rn "import io.github.KevinMitsi.inventories.\(application\|infrastructure\)" \
     src/main/java/io/github/KevinMitsi/inventories/domain/

grep -rn "import io.github.KevinMitsi.inventories.infrastructure" \
     src/main/java/io/github/KevinMitsi/inventories/application/
```

### 2.3 Qué compra esto y qué cuesta

**Cuesta**: por cada agregado hay un modelo de dominio *y* una entidad JPA *y* un mapeador;
un DTO de petición *y* un comando. Es más código.

**Compra**, y esto es lo que justifica el coste en un sistema cuyo invariante central es la
consistencia del inventario:

- **Las reglas de negocio se prueban sin infraestructura.** `BranchTest` y
  `BranchServiceTest` corren en milisegundos, sin Docker, sin contexto de Spring. Cuando
  lleguen las reglas de verdad —validar stock antes de una venta, recalcular el costo
  promedio ponderado, resolver una recepción parcial— poder ejercitarlas así es la
  diferencia entre probarlas a fondo o no probarlas.
- **El contrato público y el modelo interno evolucionan por separado.** Un campo nuevo en el
  dominio no se filtra a los clientes de la API; un renombrado interno no rompe a nadie.
- **JPA no dicta el diseño del dominio.** Sin la separación, el modelo acabaría con
  constructor vacío, campos no finales y carga perezosa filtrándose a las reglas.

---

## 3. Decisiones de diseño y su justificación

### DEC-01 — Modelo de dominio en Java puro

`domain/` no contiene ni una anotación: ni JPA, ni Jackson, ni Spring, ni Lombok. Solo
`record`, `enum` y clases normales.

**Por qué**: es lo que hace cierta la afirmación "el dominio no depende de nada". Con
Lombok sería casi cierto —desaparece del bytecode— pero "casi" invita a que la siguiente
anotación sí pese. La regla es más fácil de sostener siendo absoluta.

**Consecuencia visible**: `Branch` no tiene asignadores. Se construye por
`Branch.create(...)` o `Branch.reconstitute(...)`, y se modifica por métodos con nombre de
intención (`updateDetails`, `deactivate`). **No existe forma de obtener una instancia en
estado inválido.**

### DEC-02 — Excepciones repartidas entre `domain` y `application`

| Paquete | Contiene | Motivo |
|---|---|---|
| `domain/exception` | `DomainException`, `DomainErrorCode`, `DomainValidationException`, `InsufficientStockException`, `BusinessRuleViolationException`, `InvalidStateTransitionException` | Invariantes del propio modelo. `Quantity` necesita lanzar al validarse, y no puede importar de `application`. |
| `application/exception` | `ResourceNotFoundException`, `DuplicateResourceException`, `OperationNotPermittedException`, `ConcurrentModificationConflictException` | Fallos de orquestación: buscar y no encontrar, comprobar unicidad, autorizar. |

Todas heredan de `DomainException`, así que `application` depende de `domain` — dirección
correcta.

### DEC-03 — El dominio no conoce HTTP

Cada excepción lleva un `DomainErrorCode` (`RESOURCE_NOT_FOUND`, `INSUFFICIENT_STOCK`…).
`ApiExceptionHandler`, en el adaptador web, es **el único punto** que traduce ese código a
un estado HTTP.

**Por qué**: si el dominio devolviera un 404, exponerlo mañana por otro transporte
obligaría a reescribirlo. Además, concentrar la correspondencia en un `switch` garantiza
que la API responda de forma coherente en todos los endpoints.

Correspondencia aplicada:

| `DomainErrorCode` | HTTP | Razonamiento |
|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | |
| `DUPLICATE_RESOURCE`, `CONCURRENT_MODIFICATION` | 409 | Conflicto con el estado actual |
| `OPERATION_NOT_PERMITTED` | 403 | Autorización, no autenticación |
| `BUSINESS_RULE_VIOLATION`, `INSUFFICIENT_STOCK`, `INVALID_STATE_TRANSITION`, `VALIDATION_ERROR` | 422 | La petición está bien formada; falla su coherencia |

### DEC-04 — Objetos de valor para cantidades e importes

`Quantity` (6 decimales), `Money` (4 decimales), `Percentage` (0–100, 2 decimales).

**Por qué**: hay productos que se miden en kg, litros o metros. En punto flotante
`0.1 + 0.2 ≠ 0.3`, y en un sistema cuyo invariante es que el saldo cuadre con sus
movimientos, ese error acumulado sería **indetectable** y corrompería el histórico
(DBD-05, DBD-06).

Además fijan la escala en el tipo, no en cada servicio. `BigDecimal` por sí solo no basta:
`new BigDecimal("1.0").equals(new BigDecimal("1.00"))` es `false`. Al normalizar la escala
en el constructor, dos cantidades que representan lo mismo son iguales.

`Money.divide` amplía la precisión intermedia y redondea **una sola vez al final**, para no
arrastrar el error de un redondeo intermedio al costo promedio ponderado (RF-23).

### DEC-05 — Paginación propia del dominio

`PageQuery` y `PageResult` en lugar de `Pageable` y `Page` de Spring Data.

**Por qué**: los puertos de salida son la frontera. Si hablaran de `Pageable`, cambiar de
tecnología de persistencia obligaría a tocar todas las interfaces de repositorio, que es
justo lo que la arquitectura pretende evitar. `PageQueryTranslator`, en infraestructura,
hace la conversión en un solo sitio.

`PageQuery` impone un **techo de 100 elementos por página**. No es comodidad: sin él, un
cliente puede pedir `size=1000000` y forzar al servidor a materializar la tabla entera
(RNF-07).

### DEC-06 — Catálogos de estado: híbrido

| Modelado como | Cuáles | Motivo |
|---|---|---|
| **Enum + `CHECK`** | estados de venta, compra y transferencia; tipo de movimiento; prioridad; tipo y resolución de incidencia; tipo de alerta | Conjuntos cerrados cuyas **transiciones ya viven en el código**. Una tabla añadiría un JOIN por consulta y una segunda fuente de verdad que el código tendría que validar igualmente. |
| **Tabla real** | `unit_of_measure`, `carrier`, `logistics_route`, `price_list`, `product_price` | El negocio **los extiende en ejecución**, sin desplegar código. |

`ENTITIES.md` §13.2 admite explícitamente el enum, y §17.2 ya usa `CHECK` para el estado de
alerta. Esta decisión extiende ese criterio de forma consistente.

### DEC-07 — Entre agregados se referencia por identificador

`BranchJpaEntity` guarda `organizationId` como `UUID`, **no** como `@ManyToOne`.

**Por qué**:

- **El N+1 entre agregados deja de ser posible.** No hay proxy que se pueda desreferenciar
  dentro de un bucle y disparar una consulta por elemento.
- **El límite del agregado queda explícito.** Cargar una sucursal no arrastra media base de
  datos por navegación accidental.
- **No se pierde integridad**: la clave foránea sigue declarada en la migración y PostgreSQL
  la aplica igual.

`@ManyToOne` y `@EntityGraph` **sí** se usan *dentro* de un agregado —una venta y sus
líneas, una transferencia y sus ítems—, donde la carga conjunta es lo correcto y el grafo
evita justamente el N+1. Eso llegará con esos agregados.

### DEC-08 — MapStruct en las dos costuras

```text
Request  →(web mapper)→ Command →  Caso de uso  → Domain →(web mapper)→ Response
                                        ↕
                              Port ← (persistence mapper) → JpaEntity
```

Genera el código **al compilar**: sin reflexión en ejecución, y con `unmappedTargetPolicy =
ERROR`, una propiedad sin correspondencia **rompe la compilación** en lugar de aparecer como
`null` en producción.

**Matiz honesto**: la dirección entidad → dominio se escribe a mano en un método `default`,
porque `Branch` no tiene constructor público. Es deliberado: pasa por `reconstitute`, que
revalida los invariantes, de modo que **un dato corrupto en la base salta al leerlo** y no
varias operaciones más tarde.

### DEC-09 — Interfaces de caso de uso segregadas

Cuatro interfaces (`CreateBranchUseCase`, `UpdateBranchUseCase`,
`ChangeBranchStatusUseCase`, `QueryBranchUseCase`), una implementación (`BranchService`).

**Por qué**: la segregación existe para quien *consume* —un controlador depende solo de lo
que invoca, y un doble de prueba implementa un único método—, no para forzar cuatro clases
que compartirían las mismas dependencias.

Que activar/desactivar tenga su propia interfaz no es cosmético: **no es lo mismo** corregir
un nombre que retirar una sucursal de toda operación futura. Consecuencias distintas,
previsiblemente permisos distintos.

### DEC-10 — Anotaciones de Spring en la capa de aplicación

`BranchService` lleva `@Service` y `@Transactional`.

**Postura**: son metadatos de cableado y demarcación, no lógica. El código no llama a
ninguna API de Spring y la clase se instancia con `new` en las pruebas. Sacarlas a una
configuración externa daría pureza nominal a cambio de **dispersar los límites
transaccionales lejos del método que los necesita**, que es donde deben leerse.

---

## 4. Base de datos

### 4.1 Migración `V1__baseline_schema.sql`

26 tablas normalizadas a 3FN. Dos desviaciones respecto a `ENTITIES.md`, ambas justificadas
en la cabecera del propio fichero:

1. Catálogos de estado híbridos (DEC-06).
2. `inventory_movement` usa **claves foráneas específicas** (`purchase_order_id`, `sale_id`,
   `transfer_id`, `adjustment_id`) en lugar de la pareja polimórfica
   `reference_type`/`reference_id`, con un `CHECK` que garantiza **como mucho una no nula**.
   Es la opción que `ENTITIES.md` §8.5 recomienda por integridad referencial.

Otras decisiones del esquema:

- `user` y `role` son palabras reservadas en PostgreSQL → `app_user`, `app_role`.
- **Índices parciales** donde aportan: unidad base única por producto, alerta abierta única
  por (inventario, tipo), inventario bajo mínimo, código de barras único solo cuando existe.
- `CHECK` de coherencia temporal en `transfer`: `approved_at ≥ requested_at ≥ …`.
- `CHECK` de pares completos: una alerta resuelta tiene fecha; una incidencia resuelta tiene
  responsable, fecha **y** tipo de resolución.

### 4.2 Migración `V2__reference_data.sql`

Solo lo que el sistema necesita para arrancar y no puede crear un usuario: los 3 roles y 9
unidades de medida. **UUID fijos**, para que el mismo identificador signifique lo mismo en
todos los entornos y se pueda referenciar desde los tests.

Los datos de demostración **no** van en una migración: ensuciarían una base productiva.

### 4.3 Configuración de rendimiento

Corrección respecto a la nota previa del proyecto: las propiedades de agrupación de
sentencias van bajo `spring.jpa.properties.hibernate.*`, **no** bajo
`spring.jpa.hibernate.*`. Hibernate no lee esa segunda ruta y la optimización quedaría
silenciosamente inactiva.

| Ajuste | Valor | Motivo |
|---|---|---|
| `hikari.maximum-pool-size` | 20 | Pool acotado: menos memoria y menos contención |
| `hikari.leak-detection-threshold` | 60 s | Detecta conexiones retenidas por un fallo de código |
| `hibernate.jdbc.batch_size` | 30 | Agrupa los INSERT de líneas y movimientos |
| `order_inserts` / `order_updates` | `true` | Necesario para que la agrupación sea efectiva |
| `reWriteBatchedInserts=true` | en la URL | PostgreSQL reescribe el lote en una sola sentencia |
| `open-in-view` | `false` | Impide que la carga perezosa se dispare fuera de la transacción |
| `ddl-auto` | `validate` | El esquema lo gobierna Flyway; Hibernate solo comprueba |
| `fail_on_pagination_over_collection_fetch` | `true` | Detecta paginación resuelta en memoria |
| `spring.threads.virtual.enabled` | `true` | Java 21: un hilo virtual por petición |

---

## 5. Corte vertical de referencia: sucursales

Recorrido completo de `POST /api/v1/organizations/{organizationId}/branches`:

```text
BranchController                  valida formato (Jakarta), no contiene lógica
  └─ BranchWebMapper              CreateBranchRequest + UUID → CreateBranchCommand
      └─ CreateBranchUseCase      (interfaz)
          └─ BranchService        ¿existe la organización? ¿código libre? → transacción
              ├─ OrganizationRepositoryPort  (interfaz)
              │   └─ OrganizationPersistenceAdapter
              ├─ Branch.create()             invariantes del agregado
              └─ BranchRepositoryPort        (interfaz)
                  └─ BranchPersistenceAdapter
                      ├─ BranchPersistenceMapper   Branch → BranchJpaEntity
                      └─ BranchJpaRepository       Spring Data
  └─ BranchWebMapper              Branch → BranchResponse
```

**El controlador no tiene ni una regla de negocio** (RNF-01), y por eso cubre seis
operaciones en pocas líneas.

### Puntos que conviene señalar en la auditoría

- **Normalización del código en dos sitios coherentes.** `Branch` normaliza a mayúsculas, y
  `BranchService` aplica la misma normalización *antes* de comprobar duplicados. Sin eso,
  `bog-01` pasaría la comprobación y moriría después contra el índice único con un error
  opaco. La prueba `normalizesCodeBeforeDuplicateCheck` lo fija.
- **Lista blanca de campos de ordenación.** El nombre llega de un parámetro de la petición
  y acaba en una consulta; `PageQueryTranslator` lo contrasta contra un conjunto cerrado.
- **Consultas por criterios** en lugar de `(:param IS NULL OR campo = :param)`, que impide a
  PostgreSQL elegir un buen plan.
- **`equals`/`hashCode` escritos a mano en las entidades JPA.** Lombok los generaría sobre
  todos los campos, lo que rompe el contrato en cuanto una instancia se modifica estando
  dentro de una colección. `hashCode` es constante para que la entidad siga localizable en
  un `HashSet` antes y después de persistir.
- **Baja lógica, nunca borrado.** La sucursal aparece en ventas, compras y movimientos
  históricos; eliminarla dejaría ese histórico sin poder explicarse (`ENTITIES.md` §30).
- **Operaciones idempotentes**: activar/desactivar dos veces no falla ni simula una
  modificación (`updatedAt` no avanza si no hubo cambio).

---

## 6. Tratamiento de errores

`ApiExceptionHandler` cubre errores de dominio, validación de cuerpo y de parámetros, JSON
mal formado, tipos incompatibles, parámetros ausentes, autenticación, autorización,
integridad referencial, bloqueo optimista, ruta inexistente y un último recurso.

Tres reglas de seguridad que sostiene (RNF-03):

1. **Nunca filtra detalles internos.** Un fallo no previsto se registra completo en el
   servidor y al cliente le llega un mensaje genérico más un `traceId` con el que el equipo
   localiza la traza exacta.
2. **Nunca devuelve el valor rechazado de un campo sensible.** Contraseñas, tokens y
   credenciales se omiten aunque falle su validación.
3. **El mensaje del driver de base de datos no sale nunca.** Contiene nombres de tablas,
   columnas y restricciones que describen el esquema interno.

Todos los errores comparten el mismo cuerpo (`ApiErrorResponse`), con un campo `code`
estable sobre el que el cliente programa su reacción — más fiable que el estado HTTP, que
agrupa causas distintas bajo el mismo número.

---

## 7. Documentación de la API

OpenAPI se genera de las anotaciones, así que **documentación y código no pueden divergir**.
Disponible en `/swagger-ui.html` y `/v3/api-docs`.

Cada endpoint documenta: `operationId`, resumen, descripción con la regla de negocio y la
historia de usuario que lo motiva, esquema de cada respuesta y **todos** los códigos de
estado posibles, incluidos los de error.

El esquema de seguridad `bearerAuth` se declara como **requisito global**: los endpoints son
privados salvo indicación contraria. Es más seguro que marcar uno a uno los protegidos,
porque olvidar una anotación no deja un recurso documentado como abierto.

---

## 8. Pruebas

| Clase | Tipo | Necesita Docker |
|---|---|---|
| `BranchTest` | Unitaria de dominio, sin contexto | No |
| `BranchServiceTest` | Unitaria con Mockito sobre los puertos | No |
| `InventoriesApplicationTests` | Integración con Testcontainers | **Sí** |

`BranchTest` cubre creación, normalización, invariantes, inmutabilidad de la identidad,
idempotencia del cambio de estado y revalidación al reconstituir.

`BranchServiceTest` cubre la orquestación: qué se valida, en qué orden, y **qué se deja de
hacer cuando algo falla** (`verifyNoInteractions`, `verify(..., never())`).

> **Aviso**: `InventoriesApplicationTests` ahora ejecuta Flyway contra un PostgreSQL real de
> Testcontainers, y `ddl-auto: validate` comprueba que las entidades JPA cuadran con el
> esquema. Es la primera vez que ambas cosas se ejercitan; **requiere Docker en marcha**.

---

## 9. Estado y siguiente paso

### Completado

- [x] Arquitectura y reglas de dependencia
- [x] Esquema físico completo (26 tablas) + datos de referencia
- [x] Configuración: pool, agrupación, transacciones, OpenAPI, perfiles
- [x] Núcleo compartido: excepciones, objetos de valor, paginación, manejo de errores
- [x] Corte vertical de sucursales, de extremo a extremo, con pruebas

### Pendiente

- [x] ~~**Seguridad real**~~ — **completado en la Fase 3**. Ver `PHASE3-SEGURIDAD.md`:
      JWT, filtro de autenticación, autorización por rol y sucursal, y arranque del
      administrador inicial. `SecurityConfig` ya no está en modo permisivo.
- [ ] Catálogo: `Category`, `Product`, `UnitOfMeasure`, `ProductUnit`
      *(`ProductUnit` se implementó en la fase 4 y se retiró en la fase 6: hoy el producto
      lleva una sola unidad y las presentaciones son variantes. Ver
      `PHASE6-CATALOGO-VARIANTES.md`.)*
- [ ] Inventario: `Inventory`, `InventoryMovement` ← **el núcleo del dominio (RN-04)**
- [ ] Compras, ventas, transferencias, logística, analítica, alertas

### Orden propuesto y por qué

1. **Seguridad**, porque casi todo lo demás necesita saber quién opera y sobre qué sucursal;
   y porque el movimiento de inventario exige registrar al responsable (RN-11).
2. **Catálogo**, porque el inventario referencia productos y unidades.
3. **Inventario y movimientos**, el corazón del sistema: *el stock nunca cambia sin un
   movimiento que lo explique* (RN-04).
4. **Compras, ventas y transferencias**, que se apoyan todas en ese mismo mecanismo.

Construir primero el motor de movimientos y montar compras, ventas y transferencias encima
evita tres implementaciones distintas de la misma regla.
