# FASE 6 — Variantes de producto: retirada de la unidad base y del factor de conversión

> Documento de refactorización. Sustituye lo que `PHASE4-CATALOGO.md` §2 (DEC-17 a DEC-20) y
> `ENTITIES.md` §7.4 dicen sobre `product_unit`, la unidad base y los factores de conversión.
> Cubre EP-03, RF-07 a RF-09, HU-07 y HU-10.

---

## 1. El problema

La v1 modelaba las presentaciones de un producto con una tabla intermedia, `product_unit`,
que ataba `product` con `unit_of_measure` y añadía un `conversion_factor`. Una de esas
presentaciones era la **unidad base** (factor `1`), y el stock se guardaba siempre en ella.
Cada línea de venta, compra o transferencia apuntaba a una presentación concreta y el sistema
traducía la cantidad multiplicando por el factor antes de tocar el inventario.

Sobre el papel es una solución elegante. En la práctica rompía la aplicación por tres sitios:

**1. La interfaz no podía explicarse.** Al dar de alta un producto el usuario tenía que
elegir una "unidad base" antes de saber para qué servía, y el concepto no corresponde a nada
que exista en el negocio: nadie en una bodega piensa "mi unidad base es la botella y la caja
vale 24 de ellas". Piensa "tengo 40 botellas y 3 bolsas".

**2. El stock dejaba de ser legible.** El saldo era un número en unidades base que había que
volver a dividir para saber cuántas cajas hay. Si además se cambiaba la unidad base, el mismo
número pasaba a significar otra cosa, y el histórico de movimientos quedaba expresado en una
referencia que ya no era la vigente.

**3. La lógica de conversión se filtraba por todo el sistema.** `SaleUseCase`,
`PurchaseOrderUseCase` y `TransferUseCase` tenían el mismo bloque repetido —cargar el
producto, localizar la presentación, multiplicar por el factor— y `PurchaseOrderUseCase`
además dividía el precio unitario por el factor para obtener el costo por unidad base. Cuatro
sitios distintos haciendo aritmética sobre el mismo concepto.

La causa de fondo: **se estaba usando un solo producto para representar cosas que el negocio
trata como productos distintos**. "Brisa Botella 1 L" y "Brisa Bolsa x 24" se compran a
precios distintos, se venden por separado y se cuentan por separado en la estantería. Que
compartieran una fila de `inventory` era el error.

---

## 2. La solución

Dos cambios, uno de quita y otro de pon.

### 2.1 Cada producto se cuenta en UNA unidad, sin factor

`product` gana una columna `unit_id` y `product_unit` desaparece. La unidad ya no es un
sistema de equivalencias: es la etiqueta de lo que se cuenta. El stock de un producto son
unidades de *su* unidad, y no hay ninguna traducción que hacer en ninguna capa.

La unidad de medida sigue siendo un catálogo global (`unit_of_measure`), sigue asignándose al
crear el producto y sigue siendo obligatoria — lo que se retira es el factor.

### 2.2 Las presentaciones pasan a ser variantes: productos completos

`product` gana una columna `parent_product_id`, apuntando a otra fila de `product`. El
producto que se crea es la **variante principal** de su familia; las demás cuelgan de él.

Una variante **no comparte nada operativo con su principal**: tiene su propio SKU, su propio
código de barras, su propia unidad, sus propias filas de `inventory`, sus propios
`inventory_movement` y su propio precio en cada lista. El enlace es exclusivamente de
catálogo: sirve para presentar juntas en la interfaz las presentaciones de un mismo artículo.

```text
Agua Brisa Botella 1 L      (principal, unidad: Unidad)        ← inventario propio
 ├── Agua Brisa Botella 1 L con gas  (variante, unidad: Unidad) ← inventario propio
 └── Agua Brisa Bolsa x 24           (variante, unidad: Paquete) ← inventario propio
```

Y el inventario queda legible sin cuentas: *X unds de Botella 1 L Brisa*, *X paq de Bolsa
Brisa x 24*.

**Las variantes son opcionales.** Un producto sin variantes es un producto normal. Se pueden
declarar al crear el producto, en la misma petición, o añadirse más tarde.

---

## 3. Decisiones de diseño

### DEC-31 — Una variante es una fila de `product`, no una tabla nueva

La alternativa era una tabla `product_variant` con su propia clave. Se descartó: la variante
necesita SKU único en la organización, stock por sucursal, movimientos, líneas de venta,
líneas de compra, líneas de transferencia y precios — es decir, **todo lo que ya tiene un
producto**. Modelarla aparte habría obligado a duplicar `inventory`, `inventory_movement`,
`sale_item` y `product_price` con una segunda clave foránea opcional en cada uno, o a
convertir esas ocho relaciones en polimórficas.

Con una auto-referencia en `product` el resto del esquema no se entera de que las variantes
existen, que es exactamente lo que dice el requisito: *la variante no es el mismo producto
ante el catálogo ni ante el inventario*.

Coste asumido: el enlace no impide que dos variantes de la misma familia se contradigan (una
activa, otra dada de baja). Es intencionado — son productos autónomos, y forzar la coherencia
sería reintroducir el acoplamiento que se acaba de quitar.

### DEC-32 — El catálogo es de un solo nivel

Una variante no admite variantes propias. Un árbol de profundidad arbitraria invita a
jerarquías que nadie sabe recorrer y que la interfaz no puede dibujar, y el caso de negocio
—presentaciones de un artículo— no lo necesita.

Se comprueba en dos sitios, y a propósito:

- **Dominio** (`Product.createVariant`): lanza `DomainValidationException` con un mensaje que
  el usuario puede leer. Es la comprobación que sirve para responder al usuario.
- **Base de datos** (trigger `tr_product_parent_must_be_principal`): la última línea. No es
  expresable con un `CHECK` porque exige mirar otra fila, de ahí el trigger. Protege contra
  cargas de datos y correcciones manuales que no pasan por la aplicación.

### DEC-33 — La unidad de medida es inmutable tras la creación

Como el SKU. El stock y todos los `inventory_movement` del producto están expresados en esa
unidad; cambiarla no convertiría nada, simplemente reinterpretaría cantidades ya registradas
—40 botellas pasarían a leerse como 40 cajas— y rompería RNF-12 (los movimientos históricos
son inmutables) sin escribir una sola fila.

Si la unidad cambia de verdad, lo correcto es dar de alta otro producto. Suele ser, además,
una variante del mismo principal.

### DEC-34 — Un precio por producto, no por presentación

`product_price` pierde `product_unit_id`; su clave de negocio pasa a ser
`(price_list_id, product_id)`. Como cada variante es un producto, tiene su propio precio en
cada lista, que es lo que el negocio quería expresar cuando ponía un precio por presentación.
Desaparece de paso la división del precio por el factor de conversión.

### DEC-35 — La unicidad del SKU se comprueba también dentro del lote

Al crear un producto con varias variantes en la misma petición, los SKU del lote todavía no
están en la base: `existsByOrganizationIdAndSku` no los ve. `ProductUseCase` acumula los SKU y
códigos de barras ya reservados en la petición y los consulta antes de ir al repositorio. Sin
esto, dos variantes con el mismo SKU pasarían la validación y reventarían contra
`uq_product_org_sku` con un error que el usuario no puede interpretar.

### DEC-36 — `ProductFamily` es un resultado de caso de uso, no un modelo de dominio

`application.port.in.result.ProductFamily(principal, variants)` agrupa un producto con sus
variantes para las respuestas del catálogo. No es un agregado: no tiene invariantes que
proteger, porque cada miembro es autónomo. Vive en `port.in.result` y no en `domain.model`
precisamente para que no se confunda con uno.

---

## 4. Cambios por capa

### 4.1 Dominio (`domain.model`)

| Clase | Cambio |
|---|---|
| `ProductUnit` | **Eliminada.** Con ella se van `conversionFactor`, `isBaseUnit` y `toBaseQuantity`. |
| `Product` | Pierde la colección `units` y todo su protocolo (`addUnit`, `changeUnitFactor`, `changeBaseUnit`, `deactivateUnit`, `activateUnit`, `requireBaseUnit`, `toBaseQuantity`). Gana `unit` (`UnitOfMeasure`, inmutable), `parentProductId` (inmutable), `createVariant(...)` e `isVariant()`. |
| `Quantity` | Pierde `toBaseUnit(factor)`. `multiply` se conserva: lo usa `Money`. |
| `SaleItem`, `PurchaseOrderItem`, `TransferItem`, `ProductPrice` | Pierden `productUnitId`. La cantidad de la línea ya está en la unidad del producto. |

`Product` deja de ser un agregado con hijos y pasa a ser una entidad simple. El invariante que
justificaba el agregado —"siempre exactamente una unidad base activa"— ya no existe, porque
ya no hay nada que convertir.

### 4.2 Puertos de entrada (`application.port.in`)

| Elemento | Cambio |
|---|---|
| `AddProductUnitCommand`, `ChangeBaseUnitCommand`, `ChangeProductUnitFactorCommand` | **Eliminados.** |
| `CreateProductCommand` | `baseUnitId` → `unitOfMeasureId`; nuevo `List<Variant> variants` (vacío por defecto). |
| `AddProductVariantCommand` | **Nuevo.** |
| `ProductFamily` (`port.in.result`) | **Nuevo.** |
| `ManageProductUseCase` | `createProduct` devuelve `ProductFamily`; `addUnit`/`changeUnitFactor`/`changeBaseUnit`/`deactivateUnit`/`activateUnit` → `addVariant`. |
| `QueryProductUseCase` | Nuevos `getProductFamily(productId)` y `listVariants(parentProductId)`. |
| `ProductSearchCriteria` | Nuevo `VariantScope` (`ALL` / `PRINCIPALS_ONLY` / `VARIANTS_ONLY`). Es un enum y no dos banderas sueltas para que no exista la combinación contradictoria. |
| `SetProductPriceCommand`, `CreateSaleCommand.Item`, `CreatePurchaseOrderCommand.Item`, `CreateTransferCommand.Item` | Pierden `productUnitId`. |
| `QueryPriceListUseCase` | `getProductPrice(priceListId, productId)`. |

### 4.3 Puertos de salida (`application.port.out`)

| Elemento | Cambio |
|---|---|
| `ProductRepositoryPort` | Pierde `clearBaseUnit(productId)`, que solo existía para forzar el orden de escritura frente a `ux_product_unit_single_base`. Gana `findVariants(parentProductId)`. |
| `ProductPriceRepositoryPort` | `findByPriceListIdAndProductIdAndProductUnitId` → `findByPriceListIdAndProductId`. |

### 4.4 Casos de uso (`domain.usecase`)

- `ProductUseCase`: reescrito. `createProduct` valida y crea el principal y sus variantes;
  `buildVariant` concentra la validación de una variante (SKU y código de barras libres,
  categoría válida, unidad existente o heredada) y la usan tanto el alta como `addVariant`
  — un solo sitio donde vive esa regla.
- `SaleUseCase`, `TransferUseCase`: `postMovement` publica la cantidad de la línea tal cual.
  Desaparece `requireProductUnit` de ambos.
- `PurchaseOrderUseCase`: la recepción publica lo recibido tal cual y el costo unitario es
  directamente `item.netUnitPrice()`, sin dividir por el factor.
- `PriceListUseCase`: pierde la validación de presentación.

### 4.5 Persistencia

| Elemento | Cambio |
|---|---|
| `ProductUnitJpaEntity` | **Eliminada.** |
| `ProductJpaEntity` | Pierde la colección `units` y `replaceUnits`. Gana `unit` (`@ManyToOne` LAZY) y `parentProductId` (columna suelta: el padre no forma parte del agregado). |
| `UnitOfMeasureJpaEntity` | Gana `@BatchSize(size = 50)` **a nivel de clase**. Hibernate no admite `@BatchSize` sobre un atributo `@ManyToOne`; declarado en la entidad destino consigue el mismo efecto: las unidades de una página de productos se resuelven con un único `IN`. |
| `ProductJpaRepository` | `@EntityGraph("unit")` en las búsquedas de uno; nuevo `findByParentProductIdOrderByNameAsc`; fuera `clearBaseUnit`. |
| `CatalogSpecifications.forProducts` | Aplica `VariantScope` con `parentProductId IS [NOT] NULL`. |
| `SaleItemJpaEntity`, `PurchaseOrderItemJpaEntity`, `TransferItemJpaEntity`, `ProductPriceJpaEntity` | Pierden `product_unit_id`. |

### 4.6 API HTTP

| Antes | Ahora |
|---|---|
| `POST /products/{id}/units` | `POST /products/{id}/variants` → `201` con la variante |
| `PATCH /products/{id}/units/{unitId}/factor` | — (eliminado) |
| `PATCH /products/{id}/base-unit` | — (eliminado) |
| `POST /products/{id}/units/{unitId}/deactivation` | — (la variante se da de baja como cualquier producto) |
| `PATCH /products/{id}/units/{unitId}/activation` | — (ídem) |
| — | `GET /products/{id}/variants` → lista de variantes |
| — | `GET /products/{id}/family` → principal + variantes |
| `GET /organizations/{id}/products` | Nuevo parámetro `scope` (`ALL` por defecto) |

`POST /organizations/{id}/products` acepta ahora un `variants` opcional y responde
`ProductFamilyResponse` (`principal` + `variants`) en lugar de `ProductResponse`.
`ProductResponse` pierde `units[]` y gana `unit` y `parentProductId`.

Ejemplo del alta con variantes:

```json
{
  "sku": "BEB-BRISA-BOT-1L",
  "name": "Agua Brisa Botella 1 L",
  "unitOfMeasureId": "22222222-0000-4000-8000-000000000001",
  "variants": [
    { "sku": "BEB-BRISA-BOT-1L-GAS", "name": "Agua Brisa Botella 1 L con gas" },
    { "sku": "BEB-BRISA-BOL-24", "name": "Agua Brisa Bolsa x 24",
      "unitOfMeasureId": "22222222-0000-4000-8000-000000000008" }
  ]
}
```

Una variante que omite `unitOfMeasureId` o `categoryId` hereda los del principal.

---

## 5. Migración `V3__product_variants_replace_product_units.sql`

Cuatro bloques, en este orden:

**1. Columnas nuevas.** `product.unit_id` se rellena con la unidad que era base del producto
(o cualquiera suya, o la unidad genérica como último recurso) y pasa a `NOT NULL`.
`product.parent_product_id` se añade con FK a `product`, un `CHECK` de no-autorreferencia, un
índice parcial y el trigger de un solo nivel.

**2. Normalización del histórico.** Las líneas que apuntaban a una presentación con factor
distinto de 1 se reexpresan en la unidad del producto: cantidad × factor, precio unitario ÷
factor. **El importe de cada línea no cambia** — se corrige el dato, no el dinero.

**3. Fusión de duplicados.** Un documento podía tener dos líneas del mismo producto en
presentaciones distintas; normalizadas, hablan de lo mismo. Se funden en una: cantidades
sumadas y precio unitario como promedio ponderado por cantidad, de modo que el total del
documento se conserva. En `product_price` no se promedia —dos precios del mismo producto en
la misma lista son una contradicción, no una suma— y sobrevive el de la presentación que era
base.

**4. Retirada.** Se eliminan las cuatro columnas `product_unit_id` con sus FK, las claves
únicas pasan de `(documento, producto, presentación)` a `(documento, producto)`, y se hace
`DROP TABLE product_unit`.

`inventory.quantity` **no se toca**: ya estaba en unidades base, que es la unidad que el
producto conserva. La migración no mueve stock, así que RN-04 (todo cambio de stock respaldado
por un movimiento) queda intacto.

> Nota de operación: la migración es de un solo sentido. El factor de conversión no se
> conserva en ninguna columna, así que revertir a `product_unit` exigiría restaurar copia de
> seguridad.

---

## 6. Efecto sobre las reglas de negocio

| Regla | Estado |
|---|---|
| RN-02 — el stock pertenece a (sucursal, producto) | **Intacta**, y ahora más literal: cada variante es un producto y tiene su propia fila de `inventory`. |
| RN-03 — no confirmar venta sin stock | **Intacta.** La comparación es directa, sin conversión previa. |
| RN-04 — todo cambio de stock lleva movimiento | **Intacta.** La migración no altera saldos. |
| RN-08/RN-09/RN-10 — transferencias | **Intactas.** Las cantidades ya no se traducen al despachar ni al recibir. |
| RNF-12 — movimientos históricos inmutables | **Reforzada** por DEC-33: al no poder cambiar la unidad, un movimiento pasado no puede cambiar de significado. |
| RF-09 — un producto se maneja en varias presentaciones | **Reinterpretada**: cada presentación es un producto (variante) en lugar de una fila de `product_unit` con factor. |

---

## 7. Pruebas

| Archivo | Qué cubre ahora |
|---|---|
| `ProductTest` | Creación de principal; `createVariant` con herencia y con anulación de categoría/unidad; rechazo de variante anidada; rechazo de autorreferencia; inmutabilidad de SKU, organización y unidad al actualizar. |
| `ProductUseCaseTest` | Alta con y sin variantes; SKU repetido **dentro de la misma petición**; nada se guarda si una variante es inválida; `addVariant` con herencia de unidad y rechazo de anidamiento; `getProductFamily` de un principal y de una variante. |
| `ProductControllerTest` | Alta con variantes por HTTP; `scope=PRINCIPALS_ONLY`; `GET /variants` y `GET /family`; `422` al colgar variante de variante; baja y alta lógicas. |
| `CatalogPersistenceMapperTest` | Ida y vuelta del producto con su unidad y del enlace de variante. |
| `ProductPersistenceAdapterTest` | `findVariants` traduce a dominio; el `save` escribe la unidad. |
| `PurchaseOrderUseCaseTest` | El nido `ReceivingWithUnitConversion` pasa a `Receiving`: lo recibido se postea tal cual y el costo es el precio neto de la línea, sin dividir. |
| `SaleServiceTest`, `TransferServiceTest`, `TransferIssueServiceTest`, `PriceListServiceTest`, `SaleTest`, `PurchaseOrderTest`, `TransferTest` | Ajustadas a las factorías de línea sin `productUnitId`. |

Estado del `./gradlew test` tras la refactorización: **444 pruebas, 440 correctas**.

Las 4 que fallan son **anteriores a este cambio y ajenas a él**:
`BranchControllerTest`, `CarrierControllerTest`, `CategoryControllerTest` y
`LogisticsRouteControllerTest` hacen `POST` sobre `/deactivation`, endpoints declarados
`@PatchMapping`, y reciben `405`. Ninguno de esos cuatro controladores ni sus pruebas se han
tocado aquí.

---

## 8. Qué queda por hacer

- **Sincronizar `ENTITIES.md`**: §7.4 (`product_unit`) y las filas `product_unit_id` de
  `sale_item`, `purchase_order_item`, `transfer_item` y `product_price` describen un esquema
  que ya no existe. Este documento es la referencia vigente mientras tanto.
- **`PHASE4-CATALOGO.md` §2**: DEC-17 a DEC-20 quedan derogadas por DEC-31 a DEC-36.
- **Interfaz**: el formulario de alta debe presentar la unidad como una etiqueta simple —"¿en
  qué se cuenta este producto?"— y las variantes como una lista opcional que se puede dejar
  vacía. Ya no hay ninguna pantalla de factores de conversión que construir.
