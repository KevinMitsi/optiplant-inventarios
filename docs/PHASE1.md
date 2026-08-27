# PLAN — Fase 1: Planeación del Sistema de Inventario Multi-Sucursal

## 1. Contexto y objetivo de la prueba técnica

El proyecto consiste en diseñar y desarrollar una aplicación Full Stack para gestionar el inventario de múltiples sucursales pertenecientes a una misma organización. La solución no será evaluada únicamente por “funcionar”; también se evaluarán la calidad del diseño, la arquitectura, la documentación y la capacidad de justificar las decisiones técnicas tomadas durante el desarrollo.

El principio que debe guiar todo el proyecto es:

> **Cada decisión de diseño debe poder responder: “¿Por qué se hizo así?”**

Por esta razón, antes de seleccionar tecnologías o definir la arquitectura, esta primera fase tiene como objetivo establecer con claridad **qué problema existe, qué debe resolver el sistema, quiénes interactúan con él, cuáles son sus funcionalidades esenciales y qué información debe persistir**.

---

# 2. ¿Cuál es la problemática?

Actualmente se necesita administrar el inventario de una organización que posee **múltiples sucursales**.

Cada sucursal debe tener autonomía para realizar sus operaciones diarias —compras, ventas, entradas, salidas y movimientos de inventario—, pero al mismo tiempo todas las sucursales forman parte de una misma red y necesitan compartir información.

El principal problema no consiste únicamente en “guardar productos”, sino en mantener un inventario distribuido de manera **coherente, consultable y trazable**.

El documento establece que cada sucursal debe poder operar independientemente, compartir información de inventario en tiempo real o near-real-time, consultar otras sucursales y realizar transferencias de mercancía entre ellas.

Esto genera varios problemas de negocio.

### 2.1 Falta de visibilidad global del inventario

Una sucursal necesita conocer:

* qué productos posee;
* cuántas unidades tiene disponibles;
* cuáles están próximos a agotarse;
* qué productos existen en otras sucursales;
* dónde podría encontrarse stock disponible.

Sin esta visibilidad sería difícil aprovechar el inventario de toda la organización.

---

### 2.2 Riesgo de inconsistencias de stock

Compras, ventas, ajustes y transferencias modifican constantemente las existencias.

El sistema debe evitar situaciones como:

* vender unidades inexistentes;
* registrar dos veces una entrada;
* incrementar inventario sin justificar su origen;
* perder trazabilidad después de un ajuste;
* recibir una transferencia sin descontarla correctamente de la sucursal de origen.

---

### 2.3 Falta de trazabilidad

Cada modificación del inventario debe poder explicar:

* qué ocurrió;
* qué producto fue afectado;
* cuánto inventario cambió;
* cuándo ocurrió;
* quién realizó la acción;
* por qué ocurrió;
* en qué sucursal ocurrió.

El documento exige explícitamente que los ingresos y retiros mantengan un historial auditable con fecha, responsable, motivo y cantidad.

---

### 2.4 Gestión compleja de transferencias

Mover inventario entre sucursales no puede ser simplemente:

```text
Sucursal A: -10
Sucursal B: +10
```

Existe un proceso de negocio:

```text
Solicitud
   ↓
Validación de disponibilidad
   ↓
Preparación
   ↓
Despacho
   ↓
En tránsito
   ↓
Recepción
   ↓
Actualización del inventario
```

Además, pueden existir **recepciones parciales o faltantes**, por lo cual es necesario conservar los estados intermedios del proceso. El documento establece específicamente solicitud, preparación, despacho, recepción completa y recepción parcial.

---

### 2.5 Falta de información para tomar decisiones

No basta con almacenar transacciones.

Los responsables necesitan conocer:

* ventas recientes;
* evolución de las ventas;
* productos de mayor demanda;
* productos de baja rotación;
* inventario próximo a agotarse;
* transferencias activas;
* desempeño de las sucursales.

Por ello la aplicación también debe funcionar como herramienta de apoyo para decisiones operativas.

---

# 3. ¿Qué vamos a solucionar?

Vamos a construir un **Sistema Centralizado de Gestión de Inventario Multi-Sucursal**.

El sistema permitirá administrar desde una misma aplicación:

```text
ORGANIZACIÓN
│
├── Sucursal A
│   ├── Inventario
│   ├── Compras
│   ├── Ventas
│   └── Transferencias
│
├── Sucursal B
│   ├── Inventario
│   ├── Compras
│   ├── Ventas
│   └── Transferencias
│
└── Sucursal N
```

Cada sucursal mantendrá independencia operacional, pero los datos estarán integrados dentro de la misma plataforma.

La solución deberá permitir principalmente:

1. administrar productos;
2. conocer stock por sucursal;
3. registrar entradas y salidas;
4. mantener trazabilidad de cada movimiento;
5. gestionar compras;
6. gestionar ventas;
7. transferir inventario entre sucursales;
8. controlar los estados logísticos de las transferencias;
9. generar alertas de reabastecimiento;
10. visualizar indicadores y estadísticas;
11. controlar qué puede realizar cada tipo de usuario.

---

# 4. ¿Cómo lo vamos a solucionar?

En esta fase todavía **no se seleccionará el stack tecnológico**. Esa decisión deberá realizarse posteriormente utilizando estos requerimientos como entrada.

A nivel conceptual, la aplicación estará dividida en tres responsabilidades principales, una separación que además es obligatoria según el documento.

```text
┌───────────────────────────┐
│         FRONTEND          │
│                           │
│ Interfaces y experiencia  │
│ del usuario               │
└─────────────┬─────────────┘
              │
              │ API
              ▼
┌───────────────────────────┐
│          BACKEND          │
│                           │
│ Reglas de negocio         │
│ Validaciones              │
│ Seguridad                 │
│ Procesos                  │
└─────────────┬─────────────┘
              │
              ▼
┌───────────────────────────┐
│       BASE DE DATOS       │
│                           │
│ Persistencia              │
│ Relaciones                │
│ Históricos                │
│ Trazabilidad              │
└───────────────────────────┘
```

El frontend deberá comunicarse exclusivamente con el backend mediante una API y no contener lógica de negocio relevante. Además, toda la aplicación deberá poder ejecutarse mediante Docker Compose.

---

# 5. Alcance funcional

Para organizar el desarrollo propongo dividir el dominio en **8 módulos funcionales**.

```text
1. Seguridad y usuarios
2. Sucursales
3. Catálogo e inventario
4. Compras
5. Ventas
6. Transferencias
7. Logística
8. Dashboard / Analítica
```

Puede existir posteriormente un noveno módulo correspondiente a la **funcionalidad adicional**, ya que el documento exige implementar al menos una funcionalidad extra que aporte valor real.

---

# 6. Actores del sistema

El documento identifica tres actores humanos principales.

## 6.1 Administrador general

Tiene visibilidad sobre toda la organización.

Responsabilidades principales:

* administrar usuarios;
* administrar sucursales;
* consultar inventarios globales;
* consultar rendimiento entre sucursales;
* supervisar operaciones;
* gestionar configuraciones generales.

---

## 6.2 Gerente de sucursal

Responsable operativo de una sucursal.

Puede:

* visualizar información de su sucursal;
* consultar inventario;
* supervisar operaciones;
* consultar reportes;
* supervisar o aprobar transferencias;
* consultar indicadores.

---

## 6.3 Operador de inventario

Usuario encargado de ejecutar operaciones cotidianas.

Puede:

* registrar compras;
* registrar ventas;
* registrar entradas;
* registrar retiros;
* consultar inventario;
* solicitar transferencias;
* preparar/despachar transferencias;
* confirmar recepciones según los permisos otorgados.

---

## 6.4 Sistema externo — opcional

El documento contempla como actor opcional sistemas externos como:

* ERP;
* POS;
* aplicaciones empresariales.

La integración se realizaría mediante API.

Para la primera versión de la prueba técnica recomiendo considerarlo **fuera del MVP**, dejando únicamente la API preparada para una posible integración futura.

---

# 7. Épicas del sistema

Antes de definir las Historias de Usuario podemos agruparlas mediante épicas.

| ID    | Épica                        |
| ----- | ---------------------------- |
| EP-01 | Autenticación y autorización |
| EP-02 | Gestión de sucursales        |
| EP-03 | Gestión de productos         |
| EP-04 | Gestión de inventario        |
| EP-05 | Compras                      |
| EP-06 | Ventas                       |
| EP-07 | Transferencias               |
| EP-08 | Logística                    |
| EP-09 | Dashboard y analítica        |
| EP-10 | Alertas y reabastecimiento   |

---

# 8. Historias de Usuario

El documento recomienda documentar funcionalidades mediante Historias de Usuario y proporciona ejemplos de ingreso de inventario, dashboard y transferencias.

A partir de los módulos obligatorios podemos construir el siguiente backlog inicial.

---

## EP-01 — Autenticación y autorización

### HU-01 — Inicio de sesión

**Como** usuario registrado,
**quiero** autenticarme en el sistema,
**para** acceder únicamente a las funcionalidades autorizadas para mi rol.

### Criterios de aceptación

* El usuario debe ingresar credenciales válidas.
* Usuarios inválidos no deben obtener acceso.
* El sistema debe identificar el rol del usuario.
* El backend debe validar la autorización de las operaciones.

---

### HU-02 — Gestión de usuarios

**Como** administrador general,
**quiero** crear, actualizar y deshabilitar usuarios,
**para** controlar quién puede acceder al sistema.

---

### HU-03 — Asignación de usuario a sucursal

**Como** administrador general,
**quiero** asociar usuarios con una sucursal y un rol,
**para** restringir sus operaciones al ámbito correspondiente.

---

# 9. EP-02 — Gestión de sucursales

### HU-04 — Crear sucursal

**Como** administrador general,
**quiero** registrar nuevas sucursales,
**para** incorporarlas a la red de inventario de la organización.

---

### HU-05 — Consultar sucursales

**Como** usuario autorizado,
**quiero** consultar las sucursales disponibles,
**para** identificar dónde se encuentra inventario dentro de la organización.

---

### HU-06 — Consultar inventario de otra sucursal

**Como** operador o gerente,
**quiero** consultar la disponibilidad de productos en otras sucursales,
**para** localizar inventario antes de solicitar una transferencia.

Esta capacidad es explícitamente requerida por el documento.

---

# 10. EP-03 — Catálogo de productos

### HU-07 — Registrar producto

**Como** usuario autorizado,
**quiero** crear productos en el catálogo,
**para** poder administrar sus existencias.

Información mínima prevista:

* SKU;
* nombre;
* descripción;
* categoría;
* unidad de medida;
* estado.

---

### HU-08 — Actualizar producto

**Como** usuario autorizado,
**quiero** modificar la información de un producto,
**para** mantener actualizado el catálogo.

---

### HU-09 — Consultar catálogo

**Como** operador de inventario,
**quiero** consultar el catálogo de productos,
**para** identificar los productos administrados por la organización.

---

### HU-10 — Gestionar unidades de medida

**Como** usuario autorizado,
**quiero** asociar diferentes unidades de medida a un producto,
**para** poder trabajar con productos que se compran, almacenan o venden en diferentes presentaciones.

El manejo de múltiples unidades de medida está expresamente solicitado.

---

# 11. EP-04 — Inventario

### HU-11 — Consultar inventario de mi sucursal

**Como** operador de inventario,
**quiero** conocer el stock disponible de cada producto en mi sucursal,
**para** controlar las existencias actuales.

---

### HU-12 — Registrar ingreso de inventario

**Como** operador de inventario,
**quiero** registrar una entrada de productos,
**para** actualizar las existencias de mi sucursal.

Motivos posibles:

* compra;
* devolución;
* ajuste;
* transferencia recibida.

---

### HU-13 — Registrar retiro de inventario

**Como** operador de inventario,
**quiero** registrar una salida de producto,
**para** reflejar correctamente las disminuciones del inventario.

Motivos:

* venta;
* merma;
* ajuste;
* transferencia enviada.

---

### HU-14 — Consultar movimientos

**Como** gerente de sucursal,
**quiero** consultar el historial de movimientos de inventario,
**para** conocer quién modificó el inventario, cuándo y por qué.

---

### HU-15 — Definir stock mínimo

**Como** gerente de sucursal,
**quiero** configurar un stock mínimo por producto,
**para** detectar necesidades de reabastecimiento.

---

### HU-16 — Recibir alerta de stock bajo

**Como** gerente de sucursal,
**quiero** recibir una alerta cuando un producto alcance su stock mínimo,
**para** tomar acciones de reabastecimiento.

---

# 12. EP-05 — Compras

El módulo debe soportar órdenes de compra, condiciones comerciales, recepción de mercancía, históricos y cálculo del costo promedio ponderado.

### HU-17 — Crear orden de compra

**Como** operador de inventario,
**quiero** registrar una orden de compra a un proveedor,
**para** gestionar el abastecimiento de productos.

---

### HU-18 — Registrar condiciones comerciales

**Como** operador de inventario,
**quiero** registrar precio, descuento y plazo de pago,
**para** conservar las condiciones de la compra.

---

### HU-19 — Confirmar recepción de compra

**Como** operador de inventario,
**quiero** confirmar la mercancía recibida,
**para** incrementar automáticamente el inventario.

---

### HU-20 — Consultar histórico de compras

**Como** gerente,
**quiero** consultar compras por proveedor y producto,
**para** analizar el comportamiento de abastecimiento.

---

### HU-21 — Calcular costo promedio ponderado

**Como** gerente,
**quiero** que el sistema recalcule el costo promedio del producto después de una compra,
**para** conocer correctamente el valor del inventario.

---

# 13. EP-06 — Ventas

El documento exige registrar producto, cantidad y precio, asociar responsable/sucursal, validar stock, soportar descuentos y listas de precios y generar un comprobante o registro consultable.

### HU-22 — Registrar venta

**Como** operador,
**quiero** registrar una venta,
**para** descontar del inventario los productos comercializados.

---

### HU-23 — Validar stock antes de vender

**Como** operador,
**quiero** que el sistema valide la existencia disponible,
**para** evitar vender cantidades superiores al stock.

---

### HU-24 — Aplicar descuentos

**Como** operador autorizado,
**quiero** aplicar descuentos a los productos vendidos,
**para** registrar correctamente las condiciones comerciales.

---

### HU-25 — Utilizar listas de precios

**Como** operador,
**quiero** seleccionar una lista de precios,
**para** aplicar diferentes políticas comerciales.

---

### HU-26 — Consultar comprobante de venta

**Como** usuario autorizado,
**quiero** consultar una venta previamente registrada,
**para** verificar sus productos, cantidades, precios y responsable.

---

# 14. EP-07 — Transferencias entre sucursales

Este es probablemente el **flujo de negocio más importante del sistema** después del inventario.

### HU-27 — Solicitar transferencia

**Como** operador de inventario,
**quiero** solicitar productos desde otra sucursal,
**para** cubrir una necesidad de inventario local.

Datos mínimos:

* sucursal origen;
* sucursal destino;
* producto;
* cantidad solicitada;
* prioridad/urgencia.

---

### HU-28 — Validar solicitud

**Como** responsable de la sucursal origen,
**quiero** revisar el stock disponible,
**para** determinar cuánto producto puedo enviar.

---

### HU-29 — Aprobar o ajustar transferencia

**Como** gerente de sucursal,
**quiero** aprobar o modificar la cantidad solicitada,
**para** evitar comprometer inventario que no está disponible.

---

### HU-30 — Registrar despacho

**Como** operador de la sucursal origen,
**quiero** registrar el despacho de una transferencia,
**para** establecer que la mercancía se encuentra en tránsito.

---

### HU-31 — Confirmar recepción completa

**Como** operador de la sucursal destino,
**quiero** confirmar la recepción completa de una transferencia,
**para** incorporar automáticamente los productos al inventario.

---

### HU-32 — Confirmar recepción parcial

**Como** operador de la sucursal destino,
**quiero** informar cantidades faltantes,
**para** mantener la trazabilidad de diferencias durante el traslado.

---

### HU-33 — Resolver faltantes

**Como** gerente,
**quiero** definir el tratamiento de una diferencia de transferencia,
**para** cerrar correctamente la incidencia.

Opciones establecidas por el documento:

* reenvío;
* ajuste;
* reclamación.

---

# 15. EP-08 — Logística

### HU-34 — Registrar información de envío

**Como** operador,
**quiero** registrar transportista y fecha estimada de llegada,
**para** realizar seguimiento del traslado.

---

### HU-35 — Consultar transferencias activas

**Como** gerente,
**quiero** visualizar todas las transferencias en curso,
**para** conocer su estado actual.

Estados iniciales:

```text
SOLICITADA
→ APROBADA
→ EN_PREPARACION
→ EN_TRANSITO
→ RECIBIDA
```

Estado alternativo:

```text
EN_TRANSITO
→ RECIBIDA_PARCIAL
→ RESOLUCION_PENDIENTE
→ RECIBIDA / CERRADA
```

---

### HU-36 — Consultar tiempos logísticos

**Como** gerente,
**quiero** comparar tiempos estimados y reales de entrega,
**para** identificar problemas de logística.

---

### HU-37 — Consultar desempeño por ruta

**Como** administrador,
**quiero** analizar tiempos y cumplimiento de las rutas,
**para** identificar cuáles son las rutas más eficientes.

Estas capacidades corresponden al módulo logístico exigido por el documento.

---

# 16. EP-09 — Dashboard y analítica

### HU-38 — Dashboard de ventas

**Como** gerente,
**quiero** visualizar las ventas del mes actual comparadas con meses anteriores,
**para** identificar tendencias.

---

### HU-39 — Analizar rotación del inventario

**Como** gerente,
**quiero** conocer productos de alta y baja demanda,
**para** mejorar mis decisiones de compra.

---

### HU-40 — Visualizar productos próximos a agotarse

**Como** gerente,
**quiero** identificar rápidamente productos con stock crítico,
**para** solicitar reabastecimiento.

---

### HU-41 — Visualizar transferencias activas

**Como** gerente,
**quiero** visualizar las transferencias en curso,
**para** conocer qué inventario está próximo a ingresar o salir.

---

### HU-42 — Comparar sucursales

**Como** administrador general,
**quiero** comparar indicadores entre sucursales,
**para** evaluar el desempeño general de la organización.

Estos indicadores forman parte del dashboard mínimo solicitado.

---

# 17. Requisitos funcionales

Podemos convertir las HUs anteriores en una especificación formal.

## Seguridad

**RF-01.** El sistema deberá autenticar usuarios.

**RF-02.** El sistema deberá autorizar operaciones según el rol del usuario.

**RF-03.** El administrador deberá poder gestionar usuarios.

**RF-04.** Cada usuario deberá estar asociado a un rol y, cuando corresponda, a una sucursal.

---

## Sucursales

**RF-05.** El administrador deberá poder gestionar sucursales.

**RF-06.** El sistema deberá permitir consultar el inventario de cualquier sucursal autorizada.

---

## Productos

**RF-07.** El sistema deberá permitir crear, consultar, modificar y gestionar productos.

**RF-08.** Cada producto deberá tener un identificador único.

**RF-09.** El sistema deberá permitir múltiples unidades de medida por producto.

---

## Inventario

**RF-10.** El sistema deberá mantener stock independiente por producto y sucursal.

**RF-11.** El sistema deberá registrar entradas de inventario.

**RF-12.** El sistema deberá registrar salidas de inventario.

**RF-13.** Cada movimiento deberá almacenar fecha, usuario, cantidad y motivo.

**RF-14.** El sistema deberá conservar el histórico de movimientos.

**RF-15.** El sistema deberá permitir configurar stock mínimo por producto y sucursal.

**RF-16.** El sistema deberá identificar productos próximos a agotarse.

---

## Compras

**RF-17.** El sistema deberá permitir gestionar proveedores.

> **Nota de planeación:** aunque la “gestión de proveedores” aparece en el documento como posible funcionalidad adicional, el módulo obligatorio de compras requiere crear órdenes de compra *a proveedores* y consultar históricos por proveedor. Por tanto, como mínimo debe existir una entidad `Proveedor`, aunque su CRUD avanzado pueda considerarse ampliación.

**RF-18.** El sistema deberá permitir crear órdenes de compra.

**RF-19.** Una orden podrá contener múltiples productos.

**RF-20.** Se deberá registrar precio unitario, descuentos y condiciones de compra.

**RF-21.** La recepción de una compra deberá incrementar automáticamente el inventario.

**RF-22.** El sistema deberá conservar históricos de compras.

**RF-23.** El sistema deberá calcular el costo promedio ponderado del inventario.

---

# 18. Requisitos funcionales — Ventas

**RF-24.** El sistema deberá permitir registrar ventas.

**RF-25.** Una venta podrá contener múltiples productos.

**RF-26.** El sistema deberá validar stock antes de confirmar una venta.

**RF-27.** Una venta confirmada deberá disminuir automáticamente el inventario.

**RF-28.** El sistema deberá soportar descuentos.

**RF-29.** El sistema deberá soportar diferentes listas de precios.

**RF-30.** El sistema deberá conservar un histórico de ventas.

---

# 19. Requisitos funcionales — Transferencias

**RF-31.** Una sucursal deberá poder solicitar una transferencia a otra.

**RF-32.** La sucursal origen deberá validar disponibilidad.

**RF-33.** La cantidad solicitada podrá ser ajustada antes del despacho.

**RF-34.** El sistema deberá registrar el despacho.

**RF-35.** El sistema deberá registrar transportista.

**RF-36.** El sistema deberá registrar fecha estimada de llegada.

**RF-37.** El sistema deberá controlar el estado de la transferencia.

**RF-38.** La recepción confirmada deberá incrementar el inventario destino.

**RF-39.** El sistema deberá soportar recepciones parciales.

**RF-40.** El sistema deberá registrar faltantes.

**RF-41.** Se deberá indicar cómo se resolverá un faltante.

---

# 20. Requisitos funcionales — Analítica

**RF-42.** El sistema deberá mostrar volumen de ventas mensual.

**RF-43.** El sistema deberá permitir comparar ventas entre períodos.

**RF-44.** El sistema deberá identificar productos de alta y baja demanda.

**RF-45.** El sistema deberá mostrar productos próximos a agotarse.

**RF-46.** El sistema deberá mostrar transferencias activas.

**RF-47.** Los administradores deberán poder comparar indicadores entre sucursales.

---

# 21. Requisitos no funcionales

El documento solicita explícitamente considerar rendimiento, seguridad, escalabilidad y usabilidad.

Los siguientes RNF convierten esa solicitud general en objetivos implementables.

---

## RNF-01 — Separación de responsabilidades

La aplicación deberá mantener separadas:

* presentación;
* lógica de negocio;
* persistencia.

La lógica de negocio deberá residir en el backend.

---

## RNF-02 — API

Toda comunicación del frontend con el backend deberá realizarse mediante una API formal.

Posibilidades admitidas por la prueba:

* REST;
* GraphQL.

La selección se realizará posteriormente durante la fase de arquitectura.

---

## RNF-03 — Seguridad

El sistema deberá:

* autenticar usuarios;
* controlar permisos por rol;
* impedir operaciones no autorizadas;
* validar datos recibidos por la API;
* proteger credenciales;
* evitar almacenar contraseñas en texto plano;
* evitar confiar en información sensible enviada únicamente por el frontend.

---

## RNF-04 — Integridad

Una transacción que afecte inventario deberá completarse correctamente o revertirse.

Ejemplo:

```text
Registrar venta
+
Registrar detalle
+
Generar movimiento
+
Descontar stock
```

No debería existir un estado donde la venta quede registrada pero el inventario no haya sido modificado.

Este será un requisito particularmente importante al seleccionar la base de datos.

---

## RNF-05 — Consistencia de inventario

El stock registrado deberá corresponder a los movimientos válidos ejecutados sobre el producto.

Las operaciones concurrentes no deberán permitir que el inventario llegue accidentalmente a valores inválidos.

---

## RNF-06 — Trazabilidad

Toda operación crítica deberá almacenar suficiente información para reconstruir:

```text
QUIÉN
QUÉ
CUÁNTO
CUÁNDO
DÓNDE
POR QUÉ
```

---

## RNF-07 — Rendimiento

Para las operaciones habituales se buscará un tiempo de respuesta objetivo inferior a:

```text
< 2 segundos
```

bajo las condiciones normales de carga previstas para la prueba técnica.

Este valor es una **decisión propuesta de planeación**, no un número especificado por el documento.

---

## RNF-08 — Escalabilidad

La solución deberá permitir incrementar:

* número de usuarios;
* sucursales;
* productos;
* movimientos;
* ventas;
* compras;

sin requerir rediseñar completamente el dominio.

---

## RNF-09 — Usabilidad

La interfaz deberá ser:

* clara;
* consistente;
* responsiva;
* navegable;
* comprensible para usuarios no técnicos.

El documento exige específicamente una interfaz responsiva en la capa de presentación.

---

## RNF-10 — Mantenibilidad

El código deberá mantener:

* separación de responsabilidades;
* nomenclatura consistente;
* estructura modular;
* documentación;
* pruebas para funcionalidades críticas.

---

## RNF-11 — Portabilidad

La aplicación deberá poder ejecutarse de manera reproducible mediante:

```bash
docker compose up
```

sin depender de configuraciones manuales complejas en el entorno local, requisito explícito de la prueba.

---

## RNF-12 — Auditabilidad

Los movimientos históricos no deberán modificarse de forma que se pierda la reconstrucción de operaciones pasadas.

Un error de inventario debería corregirse preferiblemente mediante **un nuevo movimiento de ajuste**, no modificando silenciosamente un movimiento histórico.

Esta es una regla de diseño propuesta a partir del requisito de trazabilidad.

---

# 22. Reglas de negocio principales

Estas reglas serán especialmente importantes cuando diseñemos el backend.

### RN-01

Un producto puede existir en múltiples sucursales.

### RN-02

El stock pertenece conceptualmente a la relación:

```text
Sucursal + Producto
```

y no únicamente al producto.

---

### RN-03

No se debe confirmar una venta si:

```text
cantidadSolicitada > stockDisponible
```

---

### RN-04

Todo cambio de stock debe generar un movimiento de inventario.

```text
STOCK NUNCA CAMBIA SIN MOVIMIENTO
```

Esta debería convertirse en una de las invariantes principales del dominio.

---

### RN-05

Una compra recibida genera una entrada de inventario.

---

### RN-06

Una venta confirmada genera una salida de inventario.

---

### RN-07

Una transferencia debe involucrar dos sucursales diferentes.

```text
origen != destino
```

---

### RN-08

Una transferencia solo puede despacharse si existe disponibilidad suficiente para la cantidad aprobada.

---

### RN-09

La recepción de una transferencia debe incorporar exclusivamente la cantidad realmente recibida.

---

### RN-10

Los faltantes deben conservarse como incidencias y no desaparecer del histórico.

---

### RN-11

Los movimientos confirmados deberán conservar:

* usuario;
* fecha;
* producto;
* cantidad;
* sucursal;
* tipo;
* motivo.

---

### RN-12

El administrador general puede visualizar todas las sucursales.

---

### RN-13

Un gerente opera principalmente dentro de su sucursal.

---

# 23. Entidades de dominio

Este punto será la entrada principal para posteriormente construir el **modelo entidad-relación**.

Propongo dividir las entidades en cinco grupos.

```text
Organización
Seguridad
Catálogo
Operaciones
Analítica / soporte
```

---

# 24. Entidades organizacionales

## 24.1 Organización

Representa la empresa propietaria de las sucursales.

```text
Organization
------------
id
name
createdAt
updatedAt
```

Aunque el documento habla de “una misma organización”, incluir la entidad explícitamente mejora el modelo y evita acoplar directamente todas las sucursales a una única organización implícita.

---

## 24.2 Sucursal

```text
Branch
------
id
organizationId
code
name
address
status
createdAt
updatedAt
```

Relación:

```text
Organization 1 ───── N Branch
```

---

# 25. Entidades de seguridad

## 25.1 Usuario

```text
User
----
id
branchId
roleId
name
email
passwordHash
status
createdAt
updatedAt
```

---

## 25.2 Rol

```text
Role
----
id
name
description
```

Roles iniciales:

```text
ADMIN
BRANCH_MANAGER
INVENTORY_OPERATOR
```

---

# 26. Entidades de catálogo

## 26.1 Producto

```text
Product
-------
id
sku
name
description
categoryId
defaultUnitId
status
createdAt
updatedAt
```

---

## 26.2 Categoría

```text
Category
--------
id
name
description
```

Relación:

```text
Category 1 ───── N Product
```

---

## 26.3 Unidad de medida

```text
UnitOfMeasure
-------------
id
code
name
symbol
```

Ejemplos:

```text
unidad
kg
g
litro
ml
caja
paquete
```

---

## 26.4 Unidad de producto

Debido a que el documento exige múltiples unidades por producto, probablemente necesitaremos una asociación.

```text
ProductUnit
-----------
id
productId
unitId
conversionFactor
```

Ejemplo:

```text
Producto: Bebida X

1 caja = 24 unidades
```

---

# 27. Entidades de inventario

## 27.1 Inventario

Representa el estado actual de un producto en una sucursal.

```text
Inventory
---------
id
branchId
productId
quantity
minimumStock
averageCost
updatedAt
```

Restricción conceptual:

```text
UNIQUE(branchId, productId)
```

Por tanto:

```text
Branch N ─── Inventory ─── N Product
```

---

## 27.2 Movimiento de inventario

Será una de las entidades más importantes.

```text
InventoryMovement
-----------------
id
inventoryId
branchId
productId
userId
movementType
quantity
reason
referenceType
referenceId
createdAt
```

Tipos posibles:

```text
PURCHASE_IN
SALE_OUT
TRANSFER_IN
TRANSFER_OUT
RETURN_IN
LOSS_OUT
ADJUSTMENT_IN
ADJUSTMENT_OUT
```

---

# 28. Entidades de compras

## 28.1 Proveedor

```text
Supplier
--------
id
name
taxId
email
phone
status
createdAt
updatedAt
```

---

## 28.2 Orden de compra

```text
PurchaseOrder
-------------
id
branchId
supplierId
createdBy
status
orderDate
paymentTerm
subtotal
discount
total
createdAt
updatedAt
```

---

## 28.3 Detalle de orden de compra

```text
PurchaseOrderItem
-----------------
id
purchaseOrderId
productId
unitId
quantity
receivedQuantity
unitPrice
discount
subtotal
```

Relación:

```text
PurchaseOrder 1 ───── N PurchaseOrderItem
```

---

# 29. Entidades de ventas

## 29.1 Venta

```text
Sale
----
id
branchId
userId
priceListId
status
saleDate
subtotal
discount
total
createdAt
```

---

## 29.2 Detalle de venta

```text
SaleItem
--------
id
saleId
productId
unitId
quantity
unitPrice
discount
subtotal
```

---

## 29.3 Lista de precios

```text
PriceList
---------
id
name
description
status
```

---

## 29.4 Precio de producto

```text
ProductPrice
------------
id
priceListId
productId
price
```

---

# 30. Entidades de transferencia

## 30.1 Transferencia

```text
Transfer
--------
id
originBranchId
destinationBranchId
requestedBy
approvedBy
status
priority
requestedAt
approvedAt
shippedAt
estimatedArrivalAt
receivedAt
carrierId
createdAt
updatedAt
```

---

## 30.2 Detalle de transferencia

Una transferencia puede tener varios productos.

```text
TransferItem
------------
id
transferId
productId
unitId
requestedQuantity
approvedQuantity
shippedQuantity
receivedQuantity
missingQuantity
```

---

## 30.3 Incidencia de transferencia

Permite representar faltantes.

```text
TransferIssue
-------------
id
transferItemId
issueType
missingQuantity
resolutionType
description
resolvedBy
resolvedAt
status
```

Resoluciones:

```text
RESHIPMENT
ADJUSTMENT
CLAIM
```

---

# 31. Entidades de logística

## 31.1 Transportista

```text
Carrier
-------
id
name
contact
status
```

---

## 31.2 Ruta logística

```text
LogisticsRoute
--------------
id
originBranchId
destinationBranchId
estimatedDuration
estimatedCost
priority
status
```

---

## 31.3 Historial de estados de transferencia

Recomiendo no conservar únicamente:

```text
transfer.status
```

sino también su historial:

```text
TransferStatusHistory
---------------------
id
transferId
status
changedBy
changedAt
notes
```

Esto permitirá responder:

```text
¿Cuándo fue aprobada?
¿Cuándo empezó la preparación?
¿Cuándo salió?
¿Cuánto tiempo estuvo en tránsito?
¿Cuándo llegó?
```

y soporta directamente los indicadores logísticos solicitados.

---

# 32. Mapa inicial de entidades

El modelo conceptual quedaría aproximadamente así:

```text
Organization
      │
      └── Branch
            │
            ├── User
            │
            ├── Inventory ───── Product
            │                       │
            │                       ├── Category
            │                       └── ProductUnit ── UnitOfMeasure
            │
            ├── PurchaseOrder
            │       ├── Supplier
            │       └── PurchaseOrderItem ── Product
            │
            ├── Sale
            │     └── SaleItem ── Product
            │
            └── Transfer
                  ├── TransferItem ── Product
                  ├── TransferIssue
                  ├── Carrier
                  └── TransferStatusHistory
```

A esto se sumará:

```text
InventoryMovement
```

como entidad transversal que proporciona la trazabilidad del inventario.

---

# 33. Entidades imprescindibles vs. secundarias

Para evitar sobrediseñar la prueba, las separaría así.

## Core — imprescindibles

```text
Organization
Branch
User
Role
Product
Category
UnitOfMeasure
ProductUnit
Inventory
InventoryMovement
Supplier
PurchaseOrder
PurchaseOrderItem
Sale
SaleItem
Transfer
TransferItem
TransferStatusHistory
```

## Segundo nivel

```text
PriceList
ProductPrice
Carrier
LogisticsRoute
TransferIssue
```

Esto permitirá priorizar el desarrollo sin comprometer el modelo principal.

---

# 34. Funcionalidad adicional propuesta

El documento exige implementar al menos **una funcionalidad adicional** y propone, entre otras:

* alertas inteligentes;
* predicción de demanda;
* gestión avanzada de proveedores;
* control de caducidad;
* auditoría;
* reportes exportables.

Para esta prueba propongo:

## Sistema de alertas inteligentes de inventario

Razones:

1. está directamente relacionado con el núcleo del problema;
2. utiliza información que ya posee el inventario;
3. aporta valor operativo evidente;
4. no introduce un subsistema excesivamente grande;
5. puede demostrarse fácilmente;
6. posteriormente podría evolucionar hacia predicción de demanda.

Ejemplo:

```text
Producto A
Stock actual: 7
Stock mínimo: 10

→ ALERTA: Stock bajo
```

Entidad opcional:

```text
InventoryAlert
--------------
id
inventoryId
type
message
status
createdAt
resolvedAt
```

---

# 35. Qué NO debemos resolver todavía

Para mantener una correcta separación entre las fases del desarrollo, en esta etapa **no deberíamos decidir aún**:

```text
❌ React vs Vue vs Angular
❌ Java vs Node.js vs .NET vs Python
❌ PostgreSQL vs MySQL
❌ REST vs GraphQL
❌ ORM específico
❌ JWT vs sesiones
❌ arquitectura hexagonal vs Clean Architecture
❌ monolito modular vs microservicios
❌ librerías UI
❌ cloud provider
```

Estas son decisiones de **arquitectura y diseño técnico**.

Primero debemos estabilizar:

```text
Problema
   ↓
Dominio
   ↓
Actores
   ↓
Historias de usuario
   ↓
Requisitos
   ↓
Reglas de negocio
   ↓
Entidades
   ↓
Arquitectura
   ↓
Stack
```

---

# 36. Priorización para el MVP

No todas las HUs tienen la misma importancia.

Sugiero utilizar tres prioridades.

### P0 — críticas

Sin estas funcionalidades no existe realmente el sistema.

```text
Autenticación
Sucursales
Productos
Inventario por sucursal
Movimientos
Compras
Ventas
Transferencias
Trazabilidad
Validación de stock
```

### P1 — importantes

```text
Roles y permisos completos
Stock mínimo
Alertas
Dashboard
Listas de precios
Recepciones parciales
Seguimiento logístico
```

### P2 — complementarias

```text
Reportes avanzados
Integraciones externas
Predicción de demanda
Métricas logísticas avanzadas
Gestión avanzada de proveedores
```

---

# 37. Flujos críticos que debemos modelar posteriormente

El documento requiere como mínimo diagramas de flujo para **transferencias y ventas**, además del diagrama de casos de uso, arquitectura y modelo E-R.

Antes de comenzar implementación deberíamos definir estos cuatro flujos.

### Flujo A — Venta

```text
Crear venta
↓
Agregar productos
↓
Validar stock
↓
Calcular precios/descuentos
↓
Confirmar
↓
Registrar venta
↓
Crear movimientos
↓
Disminuir inventario
```

### Flujo B — Compra

```text
Crear orden
↓
Agregar productos
↓
Confirmar orden
↓
Recibir mercancía
↓
Crear movimientos
↓
Incrementar inventario
↓
Recalcular costo promedio
```

### Flujo C — Transferencia

```text
Solicitar
↓
Validar
↓
Aprobar
↓
Preparar
↓
Despachar
↓
En tránsito
↓
Recibir
↓
Actualizar inventario
```

### Flujo D — Ajuste

```text
Identificar diferencia
↓
Registrar motivo
↓
Autorizar ajuste
↓
Crear movimiento
↓
Actualizar inventario
```

---

# 38. Supuestos iniciales

El documento solicita documentar supuestos y dependencias.

Para continuar con el diseño propongo estos supuestos iniciales:

**S-01.** Existe una sola organización en la instalación inicial.

**S-02.** Una sucursal posee múltiples productos.

**S-03.** El catálogo de productos es compartido entre todas las sucursales.

**S-04.** El stock es independiente por sucursal.

**S-05.** Los precios pueden ser compartidos mediante listas de precios.

**S-06.** Una venta pertenece exclusivamente a una sucursal.

**S-07.** Una compra pertenece exclusivamente a una sucursal.

**S-08.** Una transferencia tiene exactamente una sucursal origen y una destino.

**S-09.** El sistema no permitirá stock negativo por una venta normal.

**S-10.** Las operaciones que afectan stock deberán ejecutarse de forma transaccional.

**S-11.** El cálculo del costo promedio ponderado se efectuará en las entradas originadas por compras.

**S-12.** El cliente final de una venta no es una entidad obligatoria porque el documento no exige gestión de clientes.

Este último punto es importante: **no deberíamos inventar un módulo CRM/Clientes si la prueba no lo necesita**.

---

# 39. Restricciones técnicas ya conocidas

Aunque todavía no seleccionaremos tecnologías, existen restricciones que sí están definidas en el documento.

### RT-01 — Arquitectura de tres capas

```text
Frontend
Backend
Base de datos
```

### RT-02 — Comunicación mediante API

```text
Frontend → API → Backend
```

No se acepta lógica de negocio relevante en el cliente.

### RT-03 — Docker

La solución completa debe ejecutarse mediante Docker Compose.

### RT-04 — Stack libre

La tecnología puede seleccionarse libremente, pero debe justificarse.

### RT-05 — Documentación de arquitectura

Deberemos justificar posteriormente:

* lenguaje del backend;
* base de datos;
* autenticación/autorización;
* sincronización entre sucursales;
* patrones de diseño utilizados.

---

# 40. Definition of Done general

Podemos establecer desde ahora una definición básica de terminación para cada funcionalidad.

Una HU podrá considerarse terminada cuando:

```text
[ ] La funcionalidad existe en backend.
[ ] Sus reglas de negocio se validan en backend.
[ ] La información se persiste correctamente.
[ ] Existe una interfaz para utilizarla cuando aplique.
[ ] Los errores son gestionados.
[ ] Se respetan permisos.
[ ] Se mantiene trazabilidad cuando modifica inventario.
[ ] Existen pruebas para la lógica crítica.
[ ] La API está documentada.
[ ] Funciona dentro del entorno Docker.
```

---

# 41. Resultado de esta fase

Al finalizar esta Planeación ya tenemos identificados:

| Elemento                  | Estado                      |
| ------------------------- | --------------------------- |
| Problemática              | ✅ Definida                  |
| Objetivo                  | ✅ Definido                  |
| Alcance general           | ✅ Definido                  |
| Actores                   | ✅ Definidos                 |
| Épicas                    | ✅ Definidas                 |
| Historias de Usuario      | ✅ Backlog inicial de 42 HUs |
| Requisitos funcionales    | ✅ 47 RF iniciales           |
| Requisitos no funcionales | ✅ 12 RNF                    |
| Reglas de negocio         | ✅ 13 reglas iniciales       |
| Entidades de dominio      | ✅ Identificadas             |
| Supuestos                 | ✅ Identificados             |
| Restricciones técnicas    | ✅ Identificadas             |
| Stack tecnológico         | ⏳ Pendiente                 |
| Arquitectura              | ⏳ Pendiente                 |
| Modelo E-R definitivo     | ⏳ Pendiente                 |
| Casos de uso              | ⏳ Próxima fase              |
| Diagramas de flujo        | ⏳ Próxima fase              |

---

# 42. Conclusión de la planeación

La aplicación que debemos construir no debe entenderse simplemente como un CRUD de productos.

El núcleo real del problema es:

```text
GESTIONAR
+
COORDINAR
+
TRAZAR
+
SINCRONIZAR
```

inventario distribuido entre diferentes sucursales.

Por ello, la entidad más importante desde el punto de vista del estado probablemente será:

```text
Inventory
```

mientras que desde el punto de vista de trazabilidad será:

```text
InventoryMovement
```

y el proceso de negocio con mayor complejidad será:

```text
Transfer
```

La regla central que recomiendo adoptar desde el inicio es:

> **Todo cambio de inventario debe estar respaldado por un movimiento de inventario.**

Eso nos permitirá construir posteriormente compras, ventas, ajustes y transferencias sobre un mismo principio consistente.

La siguiente fase lógica sería utilizar este PLAN para definir **los bounded contexts/módulos del backend, relaciones definitivas de las entidades, cardinalidades del modelo E-R, estrategia transaccional y después comparar alternativas de arquitectura y stack tecnológico**. Esto encaja además con el orden recomendado por la prueba, que solicita definir arquitectura y stack con justificación antes de modelar definitivamente la base de datos e implementar la solución. 
