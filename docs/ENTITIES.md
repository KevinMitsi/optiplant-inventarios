# Modelo E-R textual normalizado hasta 3FN

## 1. Objetivo del modelo de datos

El modelo de datos debe soportar de forma íntegra los procesos principales definidos para el sistema:

* gestión multi-sucursal;
* catálogo de productos;
* inventario por sucursal;
* trazabilidad de movimientos;
* compras;
* ventas;
* transferencias entre sucursales;
* logística;
* múltiples unidades de medida;
* listas de precios;
* usuarios y roles;
* alertas de reabastecimiento;
* indicadores y consultas históricas.

El documento exige expresamente un modelo E-R completo y señala que el sistema debe conservar trazabilidad de los movimientos, manejar compras, ventas y transferencias, así como mantener separación clara entre la capa de datos y el resto de la solución.

> **Nota de diseño:** el documento no prescribe tipos SQL, claves, cardinalidades exactas ni estrategia de normalización. Lo que sigue es una **propuesta de diseño de base de datos** derivada de los requisitos funcionales y preparada para una base de datos relacional.

---

# 2. Criterios generales de diseño

El modelo seguirá estos principios:

1. **Normalización hasta Tercera Forma Normal — 3FN.**
2. Claves primarias independientes para las entidades principales.
3. Claves foráneas explícitas para garantizar integridad referencial.
4. Restricciones `UNIQUE` para claves naturales relevantes.
5. Uso de `CHECK` para impedir valores inválidos.
6. Cantidades monetarias almacenadas en `DECIMAL`, nunca `FLOAT`.
7. Cantidades de inventario también mediante `DECIMAL`, ya que pueden existir unidades fraccionables como kg, litros, metros, etc.
8. Los históricos críticos no se eliminan físicamente.
9. Los movimientos de inventario deben ser inmutables desde el punto de vista funcional.
10. El stock pertenece a la combinación:

```text
Sucursal + Producto
```

11. Las operaciones de negocio deben referenciar sus detalles mediante relaciones cabecera-detalle.
12. Los estados y clasificaciones reutilizables se modelarán como catálogos cuando aporten integridad y extensibilidad.
13. Las fechas operacionales deberán usar timestamps con zona horaria o una convención UTC consistente.

---

# 3. Convenciones de tipos de datos

Para mantener el diseño independiente del motor específico, utilizaremos tipos SQL conceptuales.

| Tipo conceptual            | Uso                             |
| -------------------------- | ------------------------------- |
| `UUID`                     | Identificadores primarios       |
| `VARCHAR(n)`               | Textos cortos                   |
| `TEXT`                     | Descripciones largas            |
| `DECIMAL(p,s)`             | Cantidades y valores monetarios |
| `BOOLEAN`                  | Indicadores binarios            |
| `DATE`                     | Fechas sin hora                 |
| `TIMESTAMP WITH TIME ZONE` | Eventos con fecha/hora          |
| `INTEGER`                  | Contadores o factores enteros   |
| `SMALLINT`                 | Prioridades u órdenes pequeños  |

Para los IDs recomiendo:

```sql
UUID
```

en lugar de enteros autoincrementales.

Razones:

* menor acoplamiento entre servicios;
* mejor preparación para integración;
* evita inferencia secuencial de registros;
* facilita generación distribuida;
* funciona correctamente en escenarios multi-sucursal.

Esto podrá reconsiderarse cuando definamos el motor de base de datos.

---

# 4. Vista general del modelo E-R

```text
ORGANIZATION
    │
    └──< BRANCH
           │
           ├──< USER >── ROLE
           │
           ├──< INVENTORY >── PRODUCT
           │                      │
           │                      ├── CATEGORY
           │                      └──< PRODUCT_UNIT >── UNIT_OF_MEASURE
           │
           ├──< INVENTORY_MOVEMENT
           │
           ├──< PURCHASE_ORDER >── SUPPLIER
           │        │
           │        └──< PURCHASE_ORDER_ITEM >── PRODUCT
           │
           ├──< SALE
           │     │
           │     └──< SALE_ITEM >── PRODUCT
           │
           └──< TRANSFER
                  │
                  ├──< TRANSFER_ITEM >── PRODUCT
                  ├──< TRANSFER_STATUS_HISTORY
                  └──< TRANSFER_ISSUE
```

Relaciones complementarias:

```text
PRICE_LIST
    │
    └──< PRODUCT_PRICE >── PRODUCT

CARRIER
    │
    └──< TRANSFER

LOGISTICS_ROUTE
    ├── origin_branch
    └── destination_branch

INVENTORY
    └──< INVENTORY_ALERT
```

---

# 5. Dominio organizacional

# 5.1 `organization`

Representa la organización propietaria de las sucursales.

Aunque actualmente el sistema está planteado para una misma organización, tenerla como entidad evita codificar implícitamente una única empresa y deja el modelo correctamente normalizado.

## Atributos

| Campo        | Tipo         | Restricción            |
| ------------ | ------------ | ---------------------- |
| `id`         | UUID         | PK, NOT NULL           |
| `code`       | VARCHAR(30)  | NOT NULL, UNIQUE       |
| `name`       | VARCHAR(150) | NOT NULL               |
| `legal_name` | VARCHAR(200) | NULL                   |
| `tax_id`     | VARCHAR(50)  | NULL, UNIQUE           |
| `active`     | BOOLEAN      | NOT NULL, DEFAULT TRUE |
| `created_at` | TIMESTAMP TZ | NOT NULL               |
| `updated_at` | TIMESTAMP TZ | NOT NULL               |

## Constraints

```sql
PRIMARY KEY (id)

UNIQUE (code)
UNIQUE (tax_id)
```

`tax_id` debería permitir `NULL`.

---

# 5.2 `branch`

Representa una sucursal de la organización.

El requisito de autonomía operativa por sucursal y visibilidad compartida constituye uno de los elementos centrales de la prueba.

## Atributos

| Campo             | Tipo         | Restricción            |
| ----------------- | ------------ | ---------------------- |
| `id`              | UUID         | PK                     |
| `organization_id` | UUID         | FK, NOT NULL           |
| `code`            | VARCHAR(30)  | NOT NULL               |
| `name`            | VARCHAR(150) | NOT NULL               |
| `address_line`    | VARCHAR(250) | NULL                   |
| `city`            | VARCHAR(100) | NULL                   |
| `country_code`    | CHAR(2)      | NULL                   |
| `phone`           | VARCHAR(30)  | NULL                   |
| `active`          | BOOLEAN      | NOT NULL, DEFAULT TRUE |
| `created_at`      | TIMESTAMP TZ | NOT NULL               |
| `updated_at`      | TIMESTAMP TZ | NOT NULL               |

## Relaciones

```text
organization 1 ───── N branch
```

Una organización puede tener muchas sucursales.

Una sucursal pertenece exactamente a una organización.

## FK

```sql
FOREIGN KEY (organization_id)
REFERENCES organization(id)
```

## Constraint recomendado

```sql
UNIQUE (organization_id, code)
```

El código de sucursal debe ser único dentro de una organización, no necesariamente de forma global.

---

# 6. Seguridad y autorización

# 6.1 `role`

Catálogo de roles.

## Datos iniciales

```text
ADMIN
BRANCH_MANAGER
INVENTORY_OPERATOR
```

El documento identifica Administrador general, Gerente de sucursal y Operador de inventario como actores principales.

## Atributos

| Campo         | Tipo         | Restricción      |
| ------------- | ------------ | ---------------- |
| `id`          | UUID         | PK               |
| `code`        | VARCHAR(50)  | NOT NULL, UNIQUE |
| `name`        | VARCHAR(100) | NOT NULL         |
| `description` | VARCHAR(250) | NULL             |

---

# 6.2 `user`

Representa usuarios del sistema.

## Atributos

| Campo             | Tipo         | Restricción            |
| ----------------- | ------------ | ---------------------- |
| `id`              | UUID         | PK                     |
| `organization_id` | UUID         | FK, NOT NULL           |
| `branch_id`       | UUID         | FK, NULL               |
| `role_id`         | UUID         | FK, NOT NULL           |
| `first_name`      | VARCHAR(100) | NOT NULL               |
| `last_name`       | VARCHAR(100) | NOT NULL               |
| `email`           | VARCHAR(254) | NOT NULL               |
| `password_hash`   | VARCHAR(255) | NOT NULL               |
| `active`          | BOOLEAN      | NOT NULL, DEFAULT TRUE |
| `last_login_at`   | TIMESTAMP TZ | NULL                   |
| `created_at`      | TIMESTAMP TZ | NOT NULL               |
| `updated_at`      | TIMESTAMP TZ | NOT NULL               |

## Relaciones

```text
organization 1 ───── N user

branch 1 ───── N user
      opcional desde user

role 1 ───── N user
```

`branch_id` puede ser `NULL` para un administrador general que no pertenezca a una sucursal específica.

## Constraints

```sql
UNIQUE (organization_id, email)
```

Además:

```sql
CHECK (email <> '')
```

La contraseña nunca se almacena en texto plano.

---

# 7. Catálogo de productos

# 7.1 `category`

Permite clasificar productos.

## Atributos

| Campo             | Tipo         | Restricción            |
| ----------------- | ------------ | ---------------------- |
| `id`              | UUID         | PK                     |
| `organization_id` | UUID         | FK, NOT NULL           |
| `code`            | VARCHAR(30)  | NOT NULL               |
| `name`            | VARCHAR(100) | NOT NULL               |
| `description`     | VARCHAR(250) | NULL                   |
| `active`          | BOOLEAN      | NOT NULL, DEFAULT TRUE |

## Constraint

```sql
UNIQUE (organization_id, code)
```

Relación:

```text
organization 1 ──── N category

category 1 ──── N product
```

---

# 7.2 `product`

Representa el producto global dentro de la organización.

No almacena stock.

Esto es importante para normalización:

```text
Producto ≠ Inventario
```

El inventario depende simultáneamente de producto y sucursal.

## Atributos

| Campo             | Tipo         | Restricción            |
| ----------------- | ------------ | ---------------------- |
| `id`              | UUID         | PK                     |
| `organization_id` | UUID         | FK, NOT NULL           |
| `category_id`     | UUID         | FK, NULL               |
| `sku`             | VARCHAR(60)  | NOT NULL               |
| `barcode`         | VARCHAR(100) | NULL                   |
| `name`            | VARCHAR(180) | NOT NULL               |
| `description`     | TEXT         | NULL                   |
| `active`          | BOOLEAN      | NOT NULL, DEFAULT TRUE |
| `created_at`      | TIMESTAMP TZ | NOT NULL               |
| `updated_at`      | TIMESTAMP TZ | NOT NULL               |

## Constraints

```sql
UNIQUE (organization_id, sku)
```

Posiblemente:

```sql
UNIQUE (organization_id, barcode)
```

si el negocio garantiza un código de barras único.

---

# 7.3 `unit_of_measure`

Catálogo global de unidades de medida.

Ejemplos:

| code | name      | symbol |
| ---- | --------- | ------ |
| UNIT | Unidad    | und    |
| KG   | Kilogramo | kg     |
| G    | Gramo     | g      |
| L    | Litro     | L      |
| ML   | Mililitro | ml     |
| BOX  | Caja      | caja   |

## Atributos

| Campo    | Tipo        | Restricción      |
| -------- | ----------- | ---------------- |
| `id`     | UUID        | PK               |
| `code`   | VARCHAR(20) | UNIQUE, NOT NULL |
| `name`   | VARCHAR(80) | NOT NULL         |
| `symbol` | VARCHAR(20) | NOT NULL         |

---

# 7.4 `product_unit`

Resuelve la relación muchos-a-muchos:

```text
Product N ───── M UnitOfMeasure
```

El documento exige múltiples unidades de medida por producto.

## Atributos

| Campo               | Tipo          | Restricción             |
| ------------------- | ------------- | ----------------------- |
| `id`                | UUID          | PK                      |
| `product_id`        | UUID          | FK, NOT NULL            |
| `unit_id`           | UUID          | FK, NOT NULL            |
| `conversion_factor` | DECIMAL(18,6) | NOT NULL                |
| `is_base_unit`      | BOOLEAN       | NOT NULL, DEFAULT FALSE |
| `active`            | BOOLEAN       | NOT NULL, DEFAULT TRUE  |

Ejemplo:

```text
Producto: Botella de agua

unidad base = botella

1 caja = 24 botellas
```

Entonces:

```text
UNIT → conversion_factor = 1
BOX  → conversion_factor = 24
```

## Constraints

```sql
UNIQUE (product_id, unit_id)

CHECK (conversion_factor > 0)
```

Debe existir exactamente una unidad base activa por producto.

Eso se puede implementar mediante:

* índice único parcial;
* trigger;
* validación de dominio.

---

# 8. Inventario

# 8.1 `inventory`

Representa el saldo actual de un producto dentro de una sucursal.

## Clave conceptual

```text
branch_id + product_id
```

## Atributos

| Campo           | Tipo          | Restricción         |
| --------------- | ------------- | ------------------- |
| `id`            | UUID          | PK                  |
| `branch_id`     | UUID          | FK, NOT NULL        |
| `product_id`    | UUID          | FK, NOT NULL        |
| `quantity`      | DECIMAL(18,6) | NOT NULL, DEFAULT 0 |
| `minimum_stock` | DECIMAL(18,6) | NOT NULL, DEFAULT 0 |
| `average_cost`  | DECIMAL(18,4) | NOT NULL, DEFAULT 0 |
| `updated_at`    | TIMESTAMP TZ  | NOT NULL            |
| `version`       | INTEGER       | NOT NULL, DEFAULT 0 |

## Constraint esencial

```sql
UNIQUE (branch_id, product_id)
```

Eso impide duplicar el inventario de un mismo producto dentro de la misma sucursal.

## Checks

```sql
CHECK (quantity >= 0)

CHECK (minimum_stock >= 0)

CHECK (average_cost >= 0)

CHECK (version >= 0)
```

La regla de stock negativo podría flexibilizarse en un futuro si el negocio lo permite, pero para la prueba se recomienda prohibirlo.

## Relaciones

```text
branch  1 ───── N inventory
product 1 ───── N inventory
```

Conceptualmente:

```text
branch N ───── M product

resuelta mediante:

inventory
```

---

# 8.2 ¿Por qué `inventory` contiene `quantity` si existen movimientos?

Porque cumple una finalidad distinta.

`inventory_movement` representa:

```text
histórico
```

mientras que `inventory.quantity` representa:

```text
estado actual
```

Consultar todo el histórico para conocer el stock sería innecesariamente costoso.

La integridad debe asegurarse mediante una transacción:

```text
crear movimiento
+
actualizar saldo
```

---

# 8.3 `inventory_movement_type`

Catálogo de tipos de movimientos.

En lugar de repetir strings arbitrarios:

```text
"Compra"
"compra"
"PURCHASE"
"purchase"
```

se normaliza mediante catálogo.

## Atributos

| Campo       | Tipo            |
| ----------- | --------------- |
| `id`        | SMALLINT / UUID |
| `code`      | VARCHAR(40)     |
| `name`      | VARCHAR(100)    |
| `direction` | CHAR(1)         |

Ejemplo:

| code           | direction |
| -------------- | --------- |
| PURCHASE_IN    | `I`       |
| SALE_OUT       | `O`       |
| TRANSFER_IN    | `I`       |
| TRANSFER_OUT   | `O`       |
| RETURN_IN      | `I`       |
| LOSS_OUT       | `O`       |
| ADJUSTMENT_IN  | `I`       |
| ADJUSTMENT_OUT | `O`       |

Constraint:

```sql
CHECK (direction IN ('I', 'O'))
```

---

# 8.4 `inventory_movement`

Registra cada cambio de stock.

El documento exige que cada ingreso o retiro quede registrado con fecha, responsable, motivo y cantidad.

## Atributos

| Campo              | Tipo            | Restricción  |
| ------------------ | --------------- | ------------ |
| `id`               | UUID            | PK           |
| `inventory_id`     | UUID            | FK, NOT NULL |
| `movement_type_id` | UUID / SMALLINT | FK, NOT NULL |
| `user_id`          | UUID            | FK, NOT NULL |
| `quantity`         | DECIMAL(18,6)   | NOT NULL     |
| `unit_cost`        | DECIMAL(18,4)   | NULL         |
| `reason`           | VARCHAR(250)    | NOT NULL     |
| `reference_type`   | VARCHAR(40)     | NULL         |
| `reference_id`     | UUID            | NULL         |
| `occurred_at`      | TIMESTAMP TZ    | NOT NULL     |
| `created_at`       | TIMESTAMP TZ    | NOT NULL     |

## Constraint

```sql
CHECK (quantity > 0)
```

No se almacenan cantidades negativas.

La dirección la determina:

```text
movement_type.direction
```

Esto evita combinaciones inconsistentes.

---

# 8.5 Referencia de origen del movimiento

Ejemplos:

```text
PURCHASE + purchase_order_id
SALE + sale_id
TRANSFER + transfer_id
ADJUSTMENT + adjustment_id
```

`reference_type` + `reference_id` constituye una asociación polimórfica.

Sin embargo, desde un enfoque de máxima integridad relacional, una alternativa más estricta sería usar FKs específicas:

```text
purchase_order_id
sale_id
transfer_id
inventory_adjustment_id
```

permitiendo únicamente una no nula.

### Recomendación

Para este proyecto prefiero **FKs específicas** si queremos maximizar integridad referencial.

Ejemplo:

| Campo               | Tipo      |
| ------------------- | --------- |
| `purchase_order_id` | UUID NULL |
| `sale_id`           | UUID NULL |
| `transfer_id`       | UUID NULL |
| `adjustment_id`     | UUID NULL |

Y un constraint:

```text
máximo una referencia de origen
```

Esto es más estricto que una relación polimórfica libre.

---

# 9. Proveedores

# 9.1 `supplier`

El módulo obligatorio de compras necesita proveedores, ya que el documento exige órdenes e histórico por proveedor.

## Atributos

| Campo             | Tipo         | Constraint   |
| ----------------- | ------------ | ------------ |
| `id`              | UUID         | PK           |
| `organization_id` | UUID         | FK, NOT NULL |
| `code`            | VARCHAR(30)  | NOT NULL     |
| `name`            | VARCHAR(180) | NOT NULL     |
| `tax_id`          | VARCHAR(50)  | NULL         |
| `email`           | VARCHAR(254) | NULL         |
| `phone`           | VARCHAR(30)  | NULL         |
| `active`          | BOOLEAN      | NOT NULL     |
| `created_at`      | TIMESTAMP TZ | NOT NULL     |
| `updated_at`      | TIMESTAMP TZ | NOT NULL     |

## Constraints

```sql
UNIQUE (organization_id, code)
```

Opcional:

```sql
UNIQUE (organization_id, tax_id)
```

---

# 10. Compras

# 10.1 `purchase_order_status`

Catálogo:

```text
DRAFT
CONFIRMED
PARTIALLY_RECEIVED
RECEIVED
CANCELLED
```

---

# 10.2 `purchase_order`

Cabecera de la orden de compra.

## Atributos

| Campo               | Tipo          | Restricción         |
| ------------------- | ------------- | ------------------- |
| `id`                | UUID          | PK                  |
| `branch_id`         | UUID          | FK, NOT NULL        |
| `supplier_id`       | UUID          | FK, NOT NULL        |
| `created_by`        | UUID          | FK, NOT NULL        |
| `status_id`         | UUID/SMALLINT | FK, NOT NULL        |
| `order_number`      | VARCHAR(40)   | NOT NULL            |
| `order_date`        | DATE          | NOT NULL            |
| `payment_term_days` | INTEGER       | NOT NULL, DEFAULT 0 |
| `notes`             | TEXT          | NULL                |
| `created_at`        | TIMESTAMP TZ  | NOT NULL            |
| `updated_at`        | TIMESTAMP TZ  | NOT NULL            |

## Constraint

```sql
UNIQUE (branch_id, order_number)

CHECK (payment_term_days >= 0)
```

---

# 10.3 Totales de compra

Para 3FN existen dos opciones.

### Opción A — calcularlos

No guardar:

```text
subtotal
discount_total
total
```

sino calcularlos a partir de los detalles.

Mayor normalización.

### Opción B — persistir snapshots

Guardar los totales para mantener el valor financiero exacto al momento de la operación.

Para una prueba técnica orientada a integridad, recomiendo:

* almacenar `unit_price`;
* almacenar `discount_amount` o porcentaje aplicado;
* calcular los totales.

Luego, si se necesita auditoría financiera estricta, se pueden persistir snapshots.

---

# 10.4 `purchase_order_item`

## Atributos

| Campo                 | Tipo          | Restricción         |
| --------------------- | ------------- | ------------------- |
| `id`                  | UUID          | PK                  |
| `purchase_order_id`   | UUID          | FK, NOT NULL        |
| `product_id`          | UUID          | FK, NOT NULL        |
| `product_unit_id`     | UUID          | FK, NOT NULL        |
| `quantity`            | DECIMAL(18,6) | NOT NULL            |
| `received_quantity`   | DECIMAL(18,6) | NOT NULL, DEFAULT 0 |
| `unit_price`          | DECIMAL(18,4) | NOT NULL            |
| `discount_percentage` | DECIMAL(5,2)  | NOT NULL, DEFAULT 0 |

## Checks

```sql
CHECK (quantity > 0)

CHECK (received_quantity >= 0)

CHECK (received_quantity <= quantity)

CHECK (unit_price >= 0)

CHECK (
  discount_percentage >= 0
  AND discount_percentage <= 100
)
```

## Restricción

Puede definirse:

```sql
UNIQUE (
    purchase_order_id,
    product_id,
    product_unit_id
)
```

si se decide impedir repetir la misma línea.

---

# 11. Ventas

# 11.1 `sale_status`

Catálogo:

```text
DRAFT
CONFIRMED
CANCELLED
```

Una venta cancelada después de confirmarse no debería borrar movimientos.

Debe generar movimientos compensatorios cuando corresponda.

---

# 11.2 `sale`

## Atributos

| Campo           | Tipo          | Restricción  |
| --------------- | ------------- | ------------ |
| `id`            | UUID          | PK           |
| `branch_id`     | UUID          | FK, NOT NULL |
| `created_by`    | UUID          | FK, NOT NULL |
| `price_list_id` | UUID          | FK, NULL     |
| `status_id`     | UUID/SMALLINT | FK           |
| `sale_number`   | VARCHAR(40)   | NOT NULL     |
| `sale_date`     | TIMESTAMP TZ  | NOT NULL     |
| `notes`         | TEXT          | NULL         |
| `created_at`    | TIMESTAMP TZ  | NOT NULL     |

## Constraint

```sql
UNIQUE (branch_id, sale_number)
```

---

# 11.3 `sale_item`

## Atributos

| Campo                 | Tipo          | Constraint         |
| --------------------- | ------------- | ------------------ |
| `id`                  | UUID          | PK                 |
| `sale_id`             | UUID          | FK, NOT NULL       |
| `product_id`          | UUID          | FK, NOT NULL       |
| `product_unit_id`     | UUID          | FK, NOT NULL       |
| `quantity`            | DECIMAL(18,6) | NOT NULL           |
| `unit_price`          | DECIMAL(18,4) | NOT NULL           |
| `discount_percentage` | DECIMAL(5,2)  | NOT NULL DEFAULT 0 |

Checks:

```sql
CHECK (quantity > 0)

CHECK (unit_price >= 0)

CHECK (
    discount_percentage BETWEEN 0 AND 100
)
```

El documento requiere producto, cantidad, precio, descuentos y diferentes listas de precios.

---

# 12. Listas de precios

# 12.1 `price_list`

## Atributos

| Campo             | Tipo         |
| ----------------- | ------------ |
| `id`              | UUID         |
| `organization_id` | UUID         |
| `code`            | VARCHAR(30)  |
| `name`            | VARCHAR(100) |
| `active`          | BOOLEAN      |
| `valid_from`      | DATE NULL    |
| `valid_until`     | DATE NULL    |

Constraint:

```sql
UNIQUE (organization_id, code)
```

Check:

```sql
CHECK (
    valid_until IS NULL
    OR valid_from IS NULL
    OR valid_until >= valid_from
)
```

---

# 12.2 `product_price`

Resuelve:

```text
PriceList N ───── M Product
```

## Atributos

| Campo             | Tipo          |
| ----------------- | ------------- |
| `id`              | UUID          |
| `price_list_id`   | UUID          |
| `product_id`      | UUID          |
| `product_unit_id` | UUID          |
| `price`           | DECIMAL(18,4) |

Constraints:

```sql
UNIQUE (
    price_list_id,
    product_id,
    product_unit_id
)

CHECK (price >= 0)
```

---

# 13. Transferencias entre sucursales

El documento define explícitamente solicitud, validación, preparación, despacho y recepción completa/parcial.

---

# 13.1 `transfer_status`

Catálogo.

```text
REQUESTED
APPROVED
IN_PREPARATION
IN_TRANSIT
PARTIALLY_RECEIVED
RECEIVED
CANCELLED
ISSUE_PENDING
CLOSED
```

---

# 13.2 `transfer_priority`

Catálogo:

```text
LOW
NORMAL
HIGH
URGENT
```

También puede ser enum, pero una tabla mantiene mayor flexibilidad.

---

# 13.3 `transfer`

Cabecera del traslado.

## Atributos

| Campo                   | Tipo         | Constraint   |
| ----------------------- | ------------ | ------------ |
| `id`                    | UUID         | PK           |
| `transfer_number`       | VARCHAR(40)  | NOT NULL     |
| `origin_branch_id`      | UUID         | FK, NOT NULL |
| `destination_branch_id` | UUID         | FK, NOT NULL |
| `requested_by`          | UUID         | FK, NOT NULL |
| `approved_by`           | UUID         | FK, NULL     |
| `status_id`             | UUID         | FK, NOT NULL |
| `priority_id`           | UUID         | FK, NOT NULL |
| `carrier_id`            | UUID         | FK, NULL     |
| `route_id`              | UUID         | FK, NULL     |
| `requested_at`          | TIMESTAMP TZ | NOT NULL     |
| `approved_at`           | TIMESTAMP TZ | NULL         |
| `shipped_at`            | TIMESTAMP TZ | NULL         |
| `estimated_arrival_at`  | TIMESTAMP TZ | NULL         |
| `received_at`           | TIMESTAMP TZ | NULL         |
| `notes`                 | TEXT         | NULL         |
| `created_at`            | TIMESTAMP TZ | NOT NULL     |
| `updated_at`            | TIMESTAMP TZ | NOT NULL     |

## Constraints

```sql
CHECK (
    origin_branch_id <> destination_branch_id
)
```

Código único:

```sql
UNIQUE (origin_branch_id, transfer_number)
```

o:

```sql
UNIQUE (transfer_number)
```

si se genera centralmente.

---

# 13.4 Constraints temporales

Algunas reglas temporales se validarán a nivel de negocio:

```text
approved_at >= requested_at

shipped_at >= approved_at

received_at >= shipped_at
```

Puede utilizarse `CHECK`, aunque como existen `NULL`, debe definirse cuidadosamente.

---

# 13.5 `transfer_item`

Una transferencia puede contener múltiples productos.

## Atributos

| Campo                | Tipo          | Restricción  |
| -------------------- | ------------- | ------------ |
| `id`                 | UUID          | PK           |
| `transfer_id`        | UUID          | FK, NOT NULL |
| `product_id`         | UUID          | FK, NOT NULL |
| `product_unit_id`    | UUID          | FK, NOT NULL |
| `requested_quantity` | DECIMAL(18,6) | NOT NULL     |
| `approved_quantity`  | DECIMAL(18,6) | NULL         |
| `shipped_quantity`   | DECIMAL(18,6) | NULL         |
| `received_quantity`  | DECIMAL(18,6) | NULL         |

Constraints:

```sql
CHECK (requested_quantity > 0)

CHECK (
 approved_quantity IS NULL
 OR approved_quantity >= 0
)

CHECK (
 approved_quantity IS NULL
 OR approved_quantity <= requested_quantity
)

CHECK (
 shipped_quantity IS NULL
 OR shipped_quantity >= 0
)

CHECK (
 approved_quantity IS NULL
 OR shipped_quantity IS NULL
 OR shipped_quantity <= approved_quantity
)

CHECK (
 received_quantity IS NULL
 OR received_quantity >= 0
)

CHECK (
 shipped_quantity IS NULL
 OR received_quantity IS NULL
 OR received_quantity <= shipped_quantity
)
```

---

# 13.6 No almacenar `missing_quantity`

Una primera versión podría tener:

```text
missing_quantity
```

pero para 3FN no es necesario.

Puede derivarse:

```text
missing_quantity =
shipped_quantity - received_quantity
```

Por tanto, preferimos **no persistirlo**.

Así evitamos inconsistencias como:

```text
shipped = 10
received = 8
missing = 5 ❌
```

---

# 14. Histórico de estados de transferencias

# 14.1 `transfer_status_history`

El estado actual vive en:

```text
transfer.status_id
```

El histórico vive aquí.

## Atributos

| Campo         | Tipo              |
| ------------- | ----------------- |
| `id`          | UUID              |
| `transfer_id` | UUID              |
| `status_id`   | UUID              |
| `changed_by`  | UUID              |
| `changed_at`  | TIMESTAMP TZ      |
| `notes`       | VARCHAR(500) NULL |

Relaciones:

```text
transfer 1 ───── N transfer_status_history

transfer_status 1 ───── N transfer_status_history

user 1 ───── N transfer_status_history
```

No deben modificarse registros históricos salvo situaciones administrativas muy excepcionales.

---

# 15. Incidencias de transferencia

# 15.1 `transfer_issue_type`

Catálogo.

Ejemplos:

```text
MISSING
DAMAGED
WRONG_PRODUCT
OTHER
```

---

# 15.2 `transfer_issue_resolution`

Catálogo.

El documento menciona:

```text
reenvío
ajuste
reclamación
```



Valores:

```text
RESHIPMENT
ADJUSTMENT
CLAIM
```

---

# 15.3 `transfer_issue`

## Atributos

| Campo              | Tipo              |
| ------------------ | ----------------- |
| `id`               | UUID              |
| `transfer_item_id` | UUID              |
| `issue_type_id`    | UUID              |
| `resolution_id`    | UUID NULL         |
| `quantity`         | DECIMAL(18,6)     |
| `description`      | VARCHAR(500)      |
| `reported_by`      | UUID              |
| `reported_at`      | TIMESTAMP TZ      |
| `resolved_by`      | UUID NULL         |
| `resolved_at`      | TIMESTAMP TZ NULL |

Constraint:

```sql
CHECK (quantity > 0)
```

---

# 16. Logística

# 16.1 `carrier`

Representa transportistas.

## Atributos

| Campo             | Tipo              |
| ----------------- | ----------------- |
| `id`              | UUID              |
| `organization_id` | UUID              |
| `code`            | VARCHAR(30)       |
| `name`            | VARCHAR(150)      |
| `phone`           | VARCHAR(30) NULL  |
| `email`           | VARCHAR(254) NULL |
| `active`          | BOOLEAN           |

Constraint:

```sql
UNIQUE (organization_id, code)
```

---

# 16.2 `logistics_route`

Representa una ruta habitual entre sucursales.

El documento requiere clasificar rutas por prioridad, costo o tiempo y analizar cumplimiento.

## Atributos

| Campo                        | Tipo               |
| ---------------------------- | ------------------ |
| `id`                         | UUID               |
| `organization_id`            | UUID               |
| `origin_branch_id`           | UUID               |
| `destination_branch_id`      | UUID               |
| `estimated_duration_minutes` | INTEGER            |
| `estimated_cost`             | DECIMAL(18,4) NULL |
| `priority`                   | SMALLINT           |
| `active`                     | BOOLEAN            |

Constraints:

```sql
CHECK (
    origin_branch_id <> destination_branch_id
)

CHECK (
    estimated_duration_minutes > 0
)

CHECK (
    estimated_cost IS NULL
    OR estimated_cost >= 0
)
```

Unique recomendado:

```sql
UNIQUE (
    origin_branch_id,
    destination_branch_id
)
```

si solo existe una ruta lógica entre ambas sucursales.

---

# 17. Alertas

La prueba obliga a controlar stock mínimo y generar alertas de reabastecimiento.

# 17.1 `inventory_alert_type`

Ejemplos:

```text
LOW_STOCK
OUT_OF_STOCK
OVERSTOCK
```

Para el MVP bastan:

```text
LOW_STOCK
OUT_OF_STOCK
```

---

# 17.2 `inventory_alert`

## Atributos

| Campo                | Tipo              |
| -------------------- | ----------------- |
| `id`                 | UUID              |
| `inventory_id`       | UUID              |
| `alert_type_id`      | UUID              |
| `status`             | VARCHAR(20)       |
| `triggered_quantity` | DECIMAL(18,6)     |
| `created_at`         | TIMESTAMP TZ      |
| `resolved_at`        | TIMESTAMP TZ NULL |

Estados:

```text
OPEN
RESOLVED
DISMISSED
```

Constraint:

```sql
CHECK (
    status IN ('OPEN', 'RESOLVED', 'DISMISSED')
)
```

Otra opción sería una tabla de estados, pero para tres valores estables un constraint es suficiente.

---

# 18. Ajustes de inventario

Dado que el documento menciona explícitamente ajustes como causa de ingresos y retiros, conviene modelarlos formalmente.

# 18.1 `inventory_adjustment`

## Atributos

| Campo         | Tipo              |
| ------------- | ----------------- |
| `id`          | UUID              |
| `branch_id`   | UUID              |
| `created_by`  | UUID              |
| `approved_by` | UUID NULL         |
| `reason`      | VARCHAR(250)      |
| `created_at`  | TIMESTAMP TZ      |
| `approved_at` | TIMESTAMP TZ NULL |

---

# 18.2 `inventory_adjustment_item`

## Atributos

| Campo            | Tipo          |
| ---------------- | ------------- |
| `id`             | UUID          |
| `adjustment_id`  | UUID          |
| `product_id`     | UUID          |
| `quantity_delta` | DECIMAL(18,6) |
| `reason`         | VARCHAR(250)  |

Aquí sí es razonable utilizar signo:

```text
+10 entrada
-3 salida
```

Constraint:

```sql
CHECK (quantity_delta <> 0)
```

Al confirmar el ajuste se genera un `inventory_movement`.

---

# 19. Relaciones principales del modelo

## Organización

```text
Organization 1 : N Branch
Organization 1 : N User
Organization 1 : N Product
Organization 1 : N Category
Organization 1 : N Supplier
Organization 1 : N PriceList
```

---

## Seguridad

```text
Role 1 : N User

Branch 1 : N User
```

---

## Productos

```text
Category 1 : N Product

Product N : M UnitOfMeasure
```

Resuelta mediante:

```text
ProductUnit
```

---

## Inventario

```text
Branch N : M Product
```

Resuelta mediante:

```text
Inventory
```

Además:

```text
Inventory 1 : N InventoryMovement

User 1 : N InventoryMovement

InventoryMovementType 1 : N InventoryMovement
```

---

## Compras

```text
Branch 1 : N PurchaseOrder

Supplier 1 : N PurchaseOrder

User 1 : N PurchaseOrder

PurchaseOrder 1 : N PurchaseOrderItem

Product 1 : N PurchaseOrderItem

ProductUnit 1 : N PurchaseOrderItem
```

---

## Ventas

```text
Branch 1 : N Sale

User 1 : N Sale

PriceList 1 : N Sale

Sale 1 : N SaleItem

Product 1 : N SaleItem

ProductUnit 1 : N SaleItem
```

---

## Transferencias

Una transferencia posee dos relaciones diferentes con `Branch`.

```text
Branch 1 : N Transfer
          como origin_branch

Branch 1 : N Transfer
          como destination_branch
```

Además:

```text
Transfer 1 : N TransferItem

Product 1 : N TransferItem

Transfer 1 : N TransferStatusHistory

TransferItem 1 : N TransferIssue

Carrier 1 : N Transfer

LogisticsRoute 1 : N Transfer
```

---

# 20. Cardinalidades resumidas

| Relación                          | Cardinalidad |
| --------------------------------- | ------------ |
| Organization → Branch             | 1:N          |
| Organization → User               | 1:N          |
| Organization → Product            | 1:N          |
| Branch → User                     | 1:N          |
| Role → User                       | 1:N          |
| Category → Product                | 1:N          |
| Product ↔ UnitOfMeasure           | N:M          |
| Branch ↔ Product                  | N:M          |
| Inventory → InventoryMovement     | 1:N          |
| Supplier → PurchaseOrder          | 1:N          |
| PurchaseOrder → PurchaseOrderItem | 1:N          |
| Product → PurchaseOrderItem       | 1:N          |
| Branch → Sale                     | 1:N          |
| Sale → SaleItem                   | 1:N          |
| Product → SaleItem                | 1:N          |
| Branch → Transfer como origen     | 1:N          |
| Branch → Transfer como destino    | 1:N          |
| Transfer → TransferItem           | 1:N          |
| Transfer → TransferStatusHistory  | 1:N          |
| TransferItem → TransferIssue      | 1:N          |
| Carrier → Transfer                | 1:N          |
| PriceList ↔ Product               | N:M          |

---

# 21. Normalización a Primera Forma Normal — 1FN

Para cumplir 1FN:

> Cada columna debe almacenar un único valor atómico.

No se permiten estructuras como:

```text
product.units = "unit,box,kg"
```

o:

```text
purchase.products =
"Product A x5, Product B x10"
```

Por eso existen:

```text
product_unit

purchase_order_item

sale_item

transfer_item
```

Otro ejemplo incorrecto:

```text
User
roles = "ADMIN,MANAGER"
```

Si en el futuro un usuario puede tener múltiples roles, debería existir:

```text
user_role
```

y no una columna con valores separados.

---

# 22. Normalización a Segunda Forma Normal — 2FN

2FN exige que todos los atributos no clave dependan de **toda la clave**.

Esto resulta especialmente relevante para las tablas asociativas.

Ejemplo:

```text
Inventory

branch_id
product_id
quantity
minimum_stock
average_cost
```

La información:

```text
quantity
minimum_stock
average_cost
```

depende de:

```text
(branch_id, product_id)
```

No únicamente del producto ni únicamente de la sucursal.

Por eso:

```text
product.stock ❌
branch.stock ❌
```

serían modelos incorrectos.

---

# 23. Normalización a Tercera Forma Normal — 3FN

3FN establece que:

> Ningún atributo no clave debe depender transitivamente de otro atributo no clave.

Ejemplo incorrecto:

```text
PurchaseOrder
-------------
supplier_id
supplier_name
supplier_email
supplier_phone
```

Tenemos:

```text
purchase_order.id
    ↓
supplier_id
    ↓
supplier_name
```

Es una dependencia transitiva.

Por eso:

```text
Supplier
```

debe ser su propia entidad.

---

# 24. Ejemplo 3FN — sucursal

Incorrecto:

```text
Sale
----
sale_id
branch_id
branch_name
branch_address
```

`branch_name` y `branch_address` no dependen de la venta.

Dependen de:

```text
branch_id
```

Por eso solo almacenamos:

```text
Sale.branch_id
```

y obtenemos la información mediante relación.

---

# 25. Ejemplo 3FN — producto

Incorrecto:

```text
Inventory
---------
product_id
product_name
category_name
quantity
```

Correcto:

```text
Inventory
    ↓
Product
    ↓
Category
```

---

# 26. Ejemplo 3FN — unidades

Incorrecto:

```text
Product
-------
unit1
unit2
unit3
```

Correcto:

```text
Product
   ↓
ProductUnit
   ↓
UnitOfMeasure
```

---

# 27. Ejemplo 3FN — estados

Incorrecto:

```text
Transfer
--------
status = "En tránsito"
```

Puede funcionar, pero repite texto y dificulta evolución.

Modelo normalizado:

```text
Transfer
   ↓
TransferStatus
```

Esto evita:

```text
EN_TRANSIT
In transit
EnTransito
En tránsito
TRANSITO
```

---

# 28. Datos calculados que NO deberían almacenarse

Para maximizar integridad, algunos datos deberían ser calculados.

## `missing_quantity`

```text
shipped_quantity - received_quantity
```

No persistir.

---

## subtotal de línea

```text
quantity
× unit_price
× descuento
```

Puede calcularse.

---

## stock faltante respecto al mínimo

```text
minimum_stock - quantity
```

No persistir.

---

## duración real de transferencia

```text
received_at - shipped_at
```

No persistir.

---

## tiempo de retraso

```text
received_at - estimated_arrival_at
```

No persistir.

---

# 29. Datos que sí conviene almacenar aunque puedan derivarse

Existe una excepción importante.

En sistemas transaccionales puede ser conveniente guardar snapshots financieros.

Ejemplo:

```text
SaleItem.unit_price
```

Aunque ese precio exista actualmente en:

```text
ProductPrice
```

debe almacenarse también en la línea de venta.

¿Por qué?

Porque si mañana cambia:

```text
ProductPrice
100 → 120
```

una venta histórica realizada a:

```text
100
```

debe continuar mostrando:

```text
100
```

Esto **no viola 3FN**, porque:

```text
SaleItem.unit_price
```

representa el precio negociado/aplicado en esa operación, no el precio vigente del catálogo.

Lo mismo aplica a:

```text
PurchaseOrderItem.unit_price
```

---

# 30. Estrategia de eliminación

Para entidades maestras recomiendo **soft delete**, mediante:

```text
active = false
```

para:

```text
Product
Branch
User
Supplier
PriceList
Carrier
Category
```

No debería hacerse:

```sql
DELETE FROM product
```

si existen movimientos históricos relacionados.

---

# 31. Entidades transaccionales que NO deben eliminarse

Deberían mantenerse históricamente:

```text
InventoryMovement
PurchaseOrder
PurchaseOrderItem
Sale
SaleItem
Transfer
TransferItem
TransferStatusHistory
TransferIssue
InventoryAdjustment
```

Una operación incorrecta debe:

```text
cancelarse
revertirse
compensarse
```

pero no desaparecer.

Esto es coherente con la trazabilidad exigida por el documento.

---

# 32. Integridad referencial propuesta

Como criterio general:

## Catálogos

```text
ON DELETE RESTRICT
```

Ejemplo:

```text
Product → Category
```

No se debería eliminar físicamente una categoría utilizada.

---

## Detalles de operaciones

Podría utilizarse:

```text
ON DELETE CASCADE
```

únicamente cuando la cabecera todavía está en estado borrador y realmente se permite su eliminación.

Sin embargo, una vez confirmada una transacción:

```text
no delete
```

---

# 33. Constraints esenciales a nivel BD

## Inventario único

```sql
UNIQUE (branch_id, product_id)
```

---

## SKU único

```sql
UNIQUE (organization_id, sku)
```

---

## Email único

```sql
UNIQUE (organization_id, email)
```

---

## Producto/unidad única

```sql
UNIQUE (product_id, unit_id)
```

---

## Ruta válida

```sql
CHECK (
    origin_branch_id <> destination_branch_id
)
```

---

## Transferencia válida

```sql
CHECK (
    origin_branch_id <> destination_branch_id
)
```

---

## Cantidades positivas

```sql
CHECK (quantity > 0)
```

---

## Stock válido

```sql
CHECK (quantity >= 0)
```

---

## Descuentos válidos

```sql
CHECK (
    discount_percentage BETWEEN 0 AND 100
)
```

---

## Precio válido

```sql
CHECK (unit_price >= 0)
```

---

# 34. Constraints que requieren lógica transaccional

Algunas reglas no deberían depender únicamente de `CHECK`.

Ejemplo:

```text
No vender más stock del disponible.
```

Porque debe evaluar simultáneamente:

```text
Sale
Inventory
InventoryMovement
```

La operación debería ejecutarse conceptualmente así:

```text
BEGIN TRANSACTION

1. bloquear/validar Inventory
2. comprobar quantity >= cantidad solicitada
3. registrar Sale
4. registrar SaleItem
5. generar InventoryMovement
6. descontar Inventory.quantity

COMMIT
```

Ante cualquier error:

```text
ROLLBACK
```

---

# 35. Invariante principal del dominio

La regla más importante del modelo de datos será:

> **El stock jamás debe cambiar sin existir un movimiento que explique el cambio.**

Formalmente:

```text
Δ Inventory.quantity
        ⇔
InventoryMovement
```

Por ejemplo:

```text
Compra        → PURCHASE_IN
Venta         → SALE_OUT
Transferencia → TRANSFER_OUT / TRANSFER_IN
Merma         → LOSS_OUT
Ajuste        → ADJUSTMENT_IN / ADJUSTMENT_OUT
```

---

# 36. Tratamiento especial de transferencias

Las transferencias requieren cuidado porque existe un intervalo temporal entre salida y recepción.

Supongamos:

```text
Sucursal A: 100 unidades
Sucursal B: 20 unidades
```

Transferencia:

```text
A → B
10 unidades
```

Al despachar:

```text
Sucursal A
100 → 90
```

Movimiento:

```text
TRANSFER_OUT = 10
```

Pero B todavía no debería aumentar.

Mientras está en tránsito:

```text
Sucursal B = 20
```

Al recibir:

```text
Sucursal B
20 → 30
```

Movimiento:

```text
TRANSFER_IN = 10
```

Esto evita mostrar inventario físicamente inexistente en la sucursal destino.

---

# 37. Caso de recepción parcial

Transferencia despachada:

```text
10
```

Recepción:

```text
8
```

Entonces:

```text
Sucursal A:
-10

Sucursal B:
+8

Faltante:
2
```

Los dos restantes se representan mediante:

```text
TransferIssue
```

No mediante manipulación artificial del inventario destino.

Esto refleja exactamente el proceso de recepción parcial exigido en el documento.

---

# 38. Modelo de dependencias funcionales

Algunas dependencias clave:

```text
organization.id
→ organization.*

branch.id
→ branch.*

product.id
→ product.*

inventory.id
→ branch_id
→ product_id
→ quantity
→ minimum_stock
→ average_cost
```

Pero además:

```text
(branch_id, product_id)
→ inventory
```

---

## Purchase

```text
purchase_order.id
→ branch_id
→ supplier_id
→ status_id
→ order_date
```

Mientras:

```text
purchase_order_item.id
→ purchase_order_id
→ product_id
→ quantity
→ unit_price
```

---

## Sale

```text
sale.id
→ branch_id
→ user_id
→ status_id
→ sale_date
```

```text
sale_item.id
→ sale_id
→ product_id
→ quantity
→ unit_price
```

---

## Transfer

```text
transfer.id
→ origin_branch
→ destination_branch
→ status
→ priority
→ timestamps
```

```text
transfer_item.id
→ transfer_id
→ product_id
→ requested_quantity
→ approved_quantity
→ shipped_quantity
→ received_quantity
```

No existen dependencias transitivas innecesarias dentro de las tablas.

---

# 39. Índices recomendados

Además de PK y `UNIQUE`, convendrá crear índices para las consultas frecuentes.

## Inventario

```sql
INDEX inventory(branch_id)

INDEX inventory(product_id)

UNIQUE INDEX inventory(branch_id, product_id)
```

---

## Movimientos

```sql
INDEX inventory_movement(inventory_id, occurred_at)

INDEX inventory_movement(user_id)

INDEX inventory_movement(movement_type_id)
```

Muy importante para históricos.

---

## Productos

```sql
INDEX product(name)

UNIQUE INDEX product(organization_id, sku)
```

---

## Compras

```sql
INDEX purchase_order(branch_id, order_date)

INDEX purchase_order(supplier_id, order_date)
```

Esto soporta el histórico requerido:

```text
compras por proveedor
```

---

## Ventas

```sql
INDEX sale(branch_id, sale_date)
```

Fundamental para:

```text
ventas del mes
ventas históricas
dashboard
```

---

## Transferencias

```sql
INDEX transfer(origin_branch_id, status_id)

INDEX transfer(destination_branch_id, status_id)

INDEX transfer(requested_at)

INDEX transfer(status_id)
```

---

# 40. Entidades que NO deberían formar parte inicialmente de la BD

Para evitar sobrearquitectura, no incluiría todavía:

```text
Customer
Invoice
Payment
AccountsReceivable
AccountsPayable
Warehouse
BinLocation
Lot
SerialNumber
Promotion
TaxRule
Currency
ERPIntegration
ForecastModel
NotificationTemplate
```

El documento no exige esos conceptos para la funcionalidad central.

Podrían aparecer posteriormente como extensiones.

---

# 41. Modelo conceptual final de tablas

## Organización y seguridad

```text
organization
branch
role
user
```

## Catálogo

```text
category
product
unit_of_measure
product_unit
```

## Inventario

```text
inventory
inventory_movement_type
inventory_movement
inventory_adjustment
inventory_adjustment_item
inventory_alert_type
inventory_alert
```

## Proveedores y compras

```text
supplier
purchase_order_status
purchase_order
purchase_order_item
```

## Ventas

```text
sale_status
sale
sale_item
price_list
product_price
```

## Transferencias

```text
transfer_status
transfer_priority
transfer
transfer_item
transfer_status_history
transfer_issue_type
transfer_issue_resolution
transfer_issue
```

## Logística

```text
carrier
logistics_route
```

---

# 42. Número aproximado de entidades

Modelo core:

```text
≈ 30 tablas
```

Pero no todas tienen la misma complejidad.

Las tablas centrales son:

```text
branch
product
inventory
inventory_movement
purchase_order
purchase_order_item
sale
sale_item
transfer
transfer_item
user
```

El resto proporciona:

```text
normalización
catálogos
integridad
trazabilidad
```

---

# 43. Núcleo relacional simplificado

La parte esencial del E-R puede resumirse como:

```text
                   ORGANIZATION
                        │
              ┌─────────┴─────────┐
              │                   │
            BRANCH              PRODUCT
              │                   │
              │        ┌──────────┴──────────┐
              │        │                     │
              └── INVENTORY ─────┘      PRODUCT_UNIT
                    │
                    │
                    └── INVENTORY_MOVEMENT


SUPPLIER
   │
   └── PURCHASE_ORDER
             │
             └── PURCHASE_ORDER_ITEM
                         │
                         └── PRODUCT


BRANCH
   │
   └── SALE
        │
        └── SALE_ITEM
                │
                └── PRODUCT


BRANCH ORIGIN ──┐
                │
              TRANSFER
                │
BRANCH DEST. ───┘
                │
                ├── TRANSFER_ITEM ─── PRODUCT
                │
                ├── TRANSFER_STATUS_HISTORY
                │
                └── TRANSFER_ISSUE
```

---

# 44. Validación final frente a 3FN

## Primera Forma Normal

✅ Todos los valores son atómicos.

✅ No existen listas dentro de columnas.

✅ Detalles múltiples están separados en tablas hijas.

---

## Segunda Forma Normal

✅ Cada atributo depende completamente de su clave.

✅ Relaciones N:M fueron separadas mediante entidades asociativas.

Ejemplos:

```text
ProductUnit
Inventory
ProductPrice
```

---

## Tercera Forma Normal

✅ Los datos descriptivos de una entidad no se duplican en otras.

No almacenamos:

```text
Sale.branch_name
PurchaseOrder.supplier_name
Inventory.product_name
Transfer.origin_branch_name
SaleItem.product_name
```

Todos esos datos se obtienen mediante relaciones.

---

# 45. Decisiones de integridad más importantes

Las siguientes decisiones deberían quedar documentadas como ADRs o decisiones arquitectónicas posteriormente:

### DBD-01

**El catálogo de productos será global para la organización y el stock pertenecerá a cada sucursal.**

---

### DBD-02

**Inventory será una proyección persistida del stock actual, mientras InventoryMovement será la fuente auditable del cambio.**

---

### DBD-03

**Todo cambio en stock debe generar obligatoriamente un InventoryMovement.**

---

### DBD-04

**Las operaciones históricas no se eliminarán físicamente.**

---

### DBD-05

**Las cantidades se almacenarán como DECIMAL y no como INTEGER para admitir unidades fraccionables.**

---

### DBD-06

**Los valores monetarios utilizarán DECIMAL y nunca tipos de punto flotante.**

---

### DBD-07

**La recepción de una transferencia modifica el inventario destino solamente cuando la mercancía es confirmada.**

---

### DBD-08

**Las cantidades faltantes no se almacenarán como dato redundante si pueden derivarse de enviado menos recibido.**

---

### DBD-09

**Los precios históricos de compras y ventas sí se almacenarán como snapshots transaccionales.**

---

### DBD-10

**Los estados operacionales con ciclo de vida relevante conservarán histórico.**

Especialmente:

```text
Transfer
```

---

# 46. Modelo E-R resultante

Con esta propuesta obtenemos una base de datos:

```text
NORMALIZADA
      ↓
hasta 3FN

CONSISTENTE
      ↓
constraints + foreign keys

TRAZABLE
      ↓
InventoryMovement + historiales

TRANSACCIONAL
      ↓
compras / ventas / transferencias

ESCALABLE
      ↓
catálogos y relaciones desacopladas
```

La entidad estructural central continúa siendo:

```text
Inventory
```

porque materializa:

```text
Branch + Product
```

pero el verdadero núcleo de integridad será:

```text
InventoryMovement
```

porque permite demostrar de dónde surgió cada variación del stock.

La arquitectura de datos debería mantener siempre la siguiente invariante:

```text
┌─────────────────────────────────────────────┐
│                                             │
│       NO EXISTE CAMBIO DE INVENTARIO        │
│                                             │
│                    SIN                      │
│                                             │
│           MOVIMIENTO DE INVENTARIO          │
│                                             │
└─────────────────────────────────────────────┘
```

Esto responde directamente a uno de los requisitos más fuertes del documento: mantener un historial auditable de cada ingreso o retiro con responsable, fecha, motivo y cantidad.

Con este modelo textual ya tenemos suficiente definición para pasar al **diagrama E-R formal y al esquema físico SQL**, donde el siguiente paso debería ser fijar el motor relacional y convertir estas entidades en tablas reales con `CREATE TABLE`, PK, FK, índices, restricciones, estrategia de locking/transacciones y migraciones.
