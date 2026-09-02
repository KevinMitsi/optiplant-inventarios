# FASE 4 — Catálogo de productos y correcciones de estilo

> Documento de auditoría. Continúa `PHASE3-SEGURIDAD.md`.
> Cubre EP-03, RF-07 a RF-09, HU-07 a HU-10.

---

## 1. Correcciones aplicadas al código existente

Antes de la funcionalidad nueva se atendieron cuatro observaciones de revisión.

### 1.1 Callbacks de auditoría en las entidades

Se añadió `AuditableJpaEntity`, una `@MappedSuperclass` con `@PrePersist` y `@PreUpdate`.
De ella heredan `BranchJpaEntity`, `OrganizationJpaEntity`, `UserJpaEntity`,
`CategoryJpaEntity` y `ProductJpaEntity`.

**Por qué una superclase y no la anotación repetida**: quedan 21 entidades por escribir, y
duplicar los dos campos y los dos callbacks en cada una es una invitación a que alguna se
olvide. Con la herencia, una entidad nueva obtiene el comportamiento por el mero hecho de
extenderla.

**Cómo convive con el dominio**, que ya asigna estas fechas:

| Callback | Comportamiento | Motivo |
|---|---|---|
| `@PrePersist` | Rellena **solo si viene nulo** | Reconstituir desde la base no debe reescribir la fecha original |
| `@PreUpdate` | Sobrescribe `updatedAt` siempre | La marca refleja el instante real de escritura |

No es duplicación con el dominio sino defensa en profundidad: cualquier camino que construya
la entidad sin pasar por el modelo dejaría columnas `NOT NULL` sin valor. Fijado por
`AuditableJpaEntityTest`.

El cambio obligó a pasar de `@Builder` a `@SuperBuilder`, para que el constructor de la
subclase alcance los campos heredados.

### 1.2 Reducción de comentarios

Se recortaron los bloques extensos de `Branch`, `User`, `ApiExceptionHandler`,
`BranchService`, los mapeadores y los adaptadores. El criterio aplicado:

| Se conserva | Se elimina |
|---|---|
| Por qué una decisión es como es | Qué hace un método evidente |
| Reglas de negocio con su identificador (RN-XX) | Repetir el nombre del método en prosa |
| Consecuencias de seguridad no obvias | Describir la firma |
| Trampas: N+1, paginación en memoria, enmascarado de sensibles | Explicar sintaxis de Java |

Las descripciones de Swagger **no** se tocaron: no son comentarios de código sino la
documentación pública que consume OpenAPI.

### 1.3 y 1.4 Patrón AAA y cobertura de infraestructura

Todas las pruebas nuevas marcan explícitamente `// Arrange`, `// Act` y `// Assert`, y se
añadieron pruebas de la capa de infraestructura, que antes no tenía ninguna. Detalle en §5.

---

## 2. Modelo del catálogo

```text
Category ──< Product ──< ProductUnit >── UnitOfMeasure
              (raíz)      (hija)          (catálogo global)
```

> **DEC-17 a DEC-20 están derogadas.** `ProductUnit`, la unidad base y el factor de
> conversión se retiraron en la fase 6: cada producto se cuenta en una sola unidad y las
> presentaciones son variantes, es decir productos completos con stock y precio propios.
> Ver `PHASE6-CATALOGO-VARIANTES.md`. Lo que sigue documenta el diseño anterior.

### DEC-17 — `Product` es agregado raíz de sus presentaciones

`Product` **contiene** su lista de `ProductUnit`, en lugar de tratarlas como entidades
sueltas con su propio repositorio.

**Por qué**: existe un invariante que ninguna restricción de columna puede expresar —
*siempre hay exactamente una unidad base activa*. Sin ella no habría forma de convertir
cantidades entre presentaciones ni de saber en qué se mide el stock. Solo el agregado, que ve
todas las presentaciones a la vez, puede garantizarlo.

Consecuencias visibles en la API: no existe `POST /product-units`. Las presentaciones se
manipulan siempre a través del producto (`POST /products/{id}/units`).

### DEC-18 — La unidad base es obligatoria desde la creación

`Product.create` exige la unidad base y la registra con factor `1`. Un producto sin ella no
podría recibir existencias.

### DEC-19 — Cambiar la base exige el nuevo factor de la anterior

`changeBaseUnit(nuevaBase, factorDeLaAnterior)`.

**Por qué no se calcula solo**: si la base pasa de botella a caja de 24, la botella pasa a
valer 1/24 = 0,041666… Ese número es periódico, y redondearlo por cuenta propia introduciría
un error sistemático en todas las conversiones futuras. El negocio decide con qué precisión
trabaja.

### DEC-20 — La unidad base no se puede retirar ni cambiar de factor

- `deactivateUnit` sobre la base → rechazado: dejaría al producto sin referencia de medida.
- `changeUnitFactor` sobre la base → rechazado: su factor es `1` por definición.

Para retirar la base hay que designar antes otra. Fijado por `ProductTest`.

---

## 3. La decisión técnica de esta fase: paginar un agregado con colección

Es el primer punto del proyecto donde se pagina algo que tiene hijos, y tiene una trampa
conocida.

**El problema**: unir una colección con `JOIN FETCH` y paginar a la vez es incompatible. La
unión multiplica las filas —un producto con 3 presentaciones produce 3 filas—, así que la base
no puede aplicar `LIMIT` correctamente. Hibernate lo resuelve trayendo **todas** las filas y
recortando en memoria. Con un catálogo grande, eso agota la memoria del servidor.

La configuración del proyecto ya lo rechaza de forma explícita:

```yaml
hibernate.query.fail_on_pagination_over_collection_fetch: true
```

**La solución aplicada**, distinta según el caso de uso:

| Consulta | Estrategia | Consultas emitidas |
|---|---|---|
| `findById`, `findBySku` (un producto) | `@EntityGraph` | 1 |
| Listado paginado | `@BatchSize(50)` en la colección | 2 fijas |

Con lotes, Hibernate resuelve la página en una consulta y las presentaciones de **todos** los
productos de esa página en una segunda con `IN (...)`. Dos consultas fijas en lugar de una por
producto, y sin paginación en memoria.

Es la tercera técnica anti-N+1 del proyecto, cada una en su sitio:

| Técnica | Dónde | Por qué ahí |
|---|---|---|
| Referencia por identificador | Entre agregados | El N+1 deja de ser posible por construcción |
| `@EntityGraph` | Asociación a-uno, o a-muchos sin paginar | Una consulta con la unión resuelta |
| `@BatchSize` | Colección **paginada** | Evita la paginación en memoria |

---

## 4. Reglas de negocio implementadas

| Regla | Dónde | Prueba |
|---|---|---|
| RF-08 — SKU único por organización | `ProductService` + índice único | `failsOnDuplicateSku` |
| RF-09 — múltiples unidades por producto | `Product.addUnit` | `addsUnit`, `rejectsDuplicateUnit` |
| Unidad base única y activa | `Product` | `alwaysExactlyOneBaseUnit` |
| Código de barras único cuando existe | `ProductService` + índice parcial | `failsOnDuplicateBarcode` |
| Categoría de la misma organización y activa | `ProductService.validateCategory` | `rejectsCategoryFromAnotherOrganization` |
| Categoría con productos activos no se da de baja | `CategoryService` | `rejectsDeactivationWithActiveProducts` |

### Dos comprobaciones que la base no puede hacer

**La categoría debe ser de la misma organización.** La clave foránea garantiza que la
categoría *existe*, no que sea la correcta. Sin esta validación se podría clasificar un
producto con una categoría de otra empresa.

**Una categoría con productos activos no se da de baja.** Quedarían clasificados en una
categoría retirada, y el catálogo perdería coherencia sin que nada lo advirtiera.

---

## 5. Pruebas

### Cobertura por capa

| Capa | Clases con prueba |
|---|---|
| Dominio | `BranchTest`, `UserTest`, `ProductTest` |
| Aplicación | `BranchServiceTest`, `AuthenticationServiceTest`, `UserServiceTest`, `ProductServiceTest`, `CategoryServiceTest` |
| Infraestructura | `JwtTokenProviderAdapterTest`, `BCryptPasswordHasherAdapterTest`, `CurrentUserProviderTest`, `PageQueryTranslatorTest`, `CatalogPersistenceMapperTest`, `UserPersistenceMapperTest`, `BranchPersistenceAdapterTest`, `ProductPersistenceAdapterTest`, `AuditableJpaEntityTest`, `ApiExceptionHandlerTest` |

**227 pruebas, todas en verde.** Ninguna necesita Docker salvo la de integración.

### Criterio sobre qué se sustituye por un doble

No es uniforme, y la diferencia importa:

| Componente | Doble | Motivo |
|---|---|---|
| Repositorios de Spring Data | **Sí** | La responsabilidad del adaptador es traducir, no consultar |
| Mapeadores de MapStruct | **No** | Lo que se comprueba es la correspondencia entre campos; con un doble no habría nada que verificar |
| `JwtTokenProviderAdapter` | **No** | Se está comprobando que la firma se verifique y que un token alterado se rechace |
| `BCryptPasswordHasherAdapter` | **No** | Se comprueba que dos cifrados de la misma clave difieran, propiedad del algoritmo real |

### Pruebas que fijan propiedades de seguridad

Varias comprueban **qué no ocurre**, que es lo que se pierde de vista en una revisión:

- `unexpectedErrorHidesInternals` — un 500 no filtra la cadena de conexión.
- `dataIntegrityHidesDriverMessage` — el nombre de la restricción no sale al cliente.
- `masksSensitiveRejectedValues` — una contraseña inválida no vuelve en la respuesta.
- `accessDeniedStaysVague` — el 403 no dice qué rol haría falta.
- `rejectsTamperedToken` — un token con el contenido alterado se rechaza.
- `rejectsUnknownRoleCode` — un rol corrupto en la base salta al leerlo.
- `rejectsUnlistedSortField` — no se puede ordenar por un campo arbitrario.

---

## 6. Endpoints añadidos

| Método | Ruta | Autorización |
|---|---|---|
| `POST` | `/api/v1/organizations/{id}/categories` | `ADMIN`, `BRANCH_MANAGER` |
| `GET` | `/api/v1/organizations/{id}/categories` | Autenticado + misma organización |
| `GET` `PUT` | `/api/v1/categories/{id}` | Consulta: autenticado · Edición: `ADMIN`, `BRANCH_MANAGER` |
| `PATCH` | `/api/v1/categories/{id}/deactivation` · `/activation` | `ADMIN`, `BRANCH_MANAGER` |
| `POST` | `/api/v1/organizations/{id}/products` | `ADMIN`, `BRANCH_MANAGER` |
| `GET` | `/api/v1/organizations/{id}/products` | Autenticado + misma organización |
| `GET` `PUT` | `/api/v1/products/{id}` | Consulta: autenticado · Edición: `ADMIN`, `BRANCH_MANAGER` |
| ~~`POST`~~ | ~~`/api/v1/products/{id}/units`~~ | Eliminado en fase 6 |
| ~~`PUT`~~ | ~~`/api/v1/products/{id}/units/{unitId}/factor`~~ | Eliminado en fase 6 |
| ~~`POST`~~ | ~~`/api/v1/products/{id}/base-unit`~~ | Eliminado en fase 6 |
| ~~`POST`~~ | ~~`/api/v1/products/{id}/units/{unitId}/deactivation` · `/activation`~~ | Eliminado en fase 6 |
| `PATCH` | `/api/v1/products/{id}/deactivation` · `/activation` | `ADMIN`, `BRANCH_MANAGER` |
| `GET` | `/api/v1/units-of-measure` · `/{id}` | Autenticado |

> **Rutas vigentes tras la fase 6.** Las cuatro rutas tachadas se retiraron con `ProductUnit`.
> En su lugar existen `POST /api/v1/products/{id}/variants`, `GET /api/v1/products/{id}/variants`
> y `GET /api/v1/products/{id}/family`; el alta de producto acepta un `variants` opcional y
> responde `ProductFamilyResponse`; y la búsqueda del catálogo acepta el parámetro `scope`.
> Las bajas y altas lógicas se hacen con `PATCH`, no con `POST`. Tabla completa en
> `PHASE6-CATALOGO-VARIANTES.md` §4.6.

El operador de inventario **consulta** el catálogo pero no lo modifica: dar de alta productos
es una decisión de catálogo, no una operación diaria.

---

## 7. Estado y siguiente paso

### Completado

- [x] Arquitectura hexagonal y esquema físico (Fase 2)
- [x] Seguridad JWT y autorización por ámbito (Fase 3)
- [x] Catálogo: `Category`, `Product`, `UnitOfMeasure`, `ProductUnit` (esta última retirada en
  la fase 6, sustituida por variantes de `Product`)
- [x] Callbacks de auditoría, estilo de comentarios, patrón AAA, pruebas de infraestructura

### Deuda de pruebas reconocida

Sin cobertura aún: `JwtAuthenticationFilter`, `SecurityErrorResponder`, `DataBootstrapper`,
`UserPersistenceAdapter`, `CategoryPersistenceAdapter`, `UnitOfMeasurePersistenceAdapter`,
`BranchPersistenceMapper`, `OrganizationPersistenceAdapter` y los controladores.

Los adaptadores y mapeadores pendientes replican patrones ya cubiertos por sus equivalentes.
Los que sí aportarían valor propio son el filtro de autenticación y los controladores con
`MockMvc`, porque ejercitan la cadena de seguridad completa.

### Siguiente: el núcleo del dominio

**Inventario y movimientos** (RN-04): *el stock nunca cambia sin un movimiento que lo
explique*.

Es la pieza sobre la que se apoyan compras, ventas y transferencias. Construir primero el
motor de movimientos y montar las tres encima evita tres implementaciones distintas de la
misma regla — y tres oportunidades de que una la incumpla.

Elementos previstos:

- `Inventory` — saldo por (sucursal, producto) con bloqueo optimista (RNF-05)
- `InventoryMovement` — bitácora inmutable con responsable, fecha, cantidad y motivo (RN-11)
- `InventoryAdjustment` — correcciones formales con responsable y motivo (Flujo D)
- `InventoryAlert` — aviso de stock bajo (HU-16)
- Costo promedio ponderado, recalculado en cada entrada por compra (RF-23)
