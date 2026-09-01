# FASE 3 — Seguridad: autenticación JWT y autorización por ámbito

> Documento de auditoría. Continúa `PHASE2-ARQUITECTURA-BACKEND.md`.
> Cubre EP-01, RF-01 a RF-04, RN-12, RN-13 y RNF-03.

---

## 1. Qué entrega esta fase

| Bloque | Contenido |
|---|---|
| Dominio | `User`, `Role`, `RoleCode` con las reglas de alcance |
| Aplicación | `AuthenticationService`, `UserService`, 4 puertos de salida, 3 puertos de entrada |
| Infraestructura | JWT, BCrypt, filtro de autenticación, entidades JPA, controladores |
| Arranque | `DataBootstrapper` para el administrador inicial |
| Pruebas | 40 pruebas nuevas (93 en total, todas en verde) |

`SecurityConfig` deja de estar en modo permisivo: **todo endpoint no declarado explícitamente
como público exige autenticación**.

---

## 2. La decisión central: BCrypt y JJWT no entran en `application`

Dos puertos de salida lo garantizan:

| Puerto | Implementación | Qué aísla |
|---|---|---|
| `PasswordHasherPort` | `BCryptPasswordHasherAdapter` | El algoritmo de hash |
| `TokenProviderPort` | `JwtTokenProviderAdapter` | El formato JWT y la biblioteca |

`AuthenticationService` pide *"cifra esto"* y *"dame un token para este usuario"*. No sabe
que existe BCrypt, ni que el token es un JWT, ni cómo se firma.

**Verificable mecánicamente** — no debe devolver nada:

```bash
grep -rEi "jsonwebtoken|bcrypt|jakarta.persistence|hibernate" \
     src/main/java/io/github/KevinMitsi/inventories/application/
```

El beneficio no es teórico: `AuthenticationServiceTest` sustituye ambos puertos por dobles y
verifica 13 escenarios de autenticación **sin generar un solo hash BCrypt ni firmar un solo
token**. Con las dependencias acopladas, cada prueba pagaría el coste deliberadamente lento
de BCrypt.

---

## 3. Decisiones y su justificación

### DEC-11 — `RoleCode` es un enum con comportamiento, no una constante

Las reglas de alcance viven como métodos en el enum:

```java
role.canOperateOnAnyBranch()    // RN-12
role.canApproveTransfers()      // HU-29
role.requiresBranch()           // coherencia rol/sucursal
```

**Por qué**: repartidas como comparaciones sueltas (`if (role == ADMIN || ...)`) por los
servicios, cambiar una regla sería una cacería por todo el código, y bastaría con olvidar un
sitio para abrir un agujero. En el enum, la regla está en un único lugar.

La tabla `app_role` sigue existiendo para la integridad referencial y los textos legibles,
pero **la lógica no está ahí**: una fila nueva en la tabla no vendría acompañada del código
que la interpretase.

### DEC-12 — El token transporta rol, sucursal y organización

Permite resolver la autorización **sin consultar la base en cada petición**, que es lo que
hace viable una API sin estado y escalable en horizontal (RNF-08).

**El precio, dicho explícitamente**: los datos del token son una fotografía del momento de
emisión. Si a un usuario se le cambia el rol o se le da de baja, su token de acceso sigue
siendo válido hasta caducar.

Dos mitigaciones:

1. El token de acceso vive **1 hora**.
2. **La renovación recarga el usuario desde la base** en lugar de fiarse del token. Es lo que
   hace efectiva una baja: el token de renovación sigue siendo criptográficamente válido,
   pero el estado real manda. Lo fija `reloadsUserFromRepository`.

### DEC-13 — Dos tokens con propósitos separados

| Token | Vida | Autoriza operaciones |
|---|---|---|
| `accessToken` | 1 h | **Sí** |
| `refreshToken` | 7 días | **No** — solo obtiene uno de acceso nuevo |

Presentar un token de renovación en la cabecera `Authorization` se rechaza en el filtro; y
presentar uno de acceso en `/auth/refresh` se rechaza en el servicio. **Sin ambas
comprobaciones, la separación no significaría nada**: el token de vida larga valdría como el
de vida corta y el objetivo de limitar la exposición se perdería.

### DEC-14 — Autorización en dos niveles, porque uno solo no basta

| Nivel | Herramienta | Resuelve |
|---|---|---|
| Rol | `@PreAuthorize("hasRole('ADMIN')")` | "Esto lo hace un administrador" |
| Datos | `CurrentUserProvider` | "¿Es *tu* sucursal? ¿*Tu* organización? ¿*Tú* mismo?" |

El segundo nivel **no puede resolverse con anotaciones**: exige comparar la sucursal o la
organización del recurso con la del solicitante, y eso solo se sabe una vez cargado el
recurso.

Ejemplo concreto en `UserController.createUser`: `@PreAuthorize` comprueba que sea
administrador, pero `requireBelongsToOrganization` es lo que impide que un administrador cree
usuarios en **otra** organización cambiando el identificador de la ruta.

### DEC-15 — El usuario del contexto se lee en infraestructura, no en los servicios

`CurrentUserProvider` vive en el adaptador de seguridad. Los servicios reciben los
identificadores **como argumentos explícitos**.

**Por qué**: si `UserService` leyera el `SecurityContextHolder`, cada prueba unitaria
tendría que montar un contexto de seguridad, y la firma de los métodos ocultaría de qué
dependen realmente. Con argumentos explícitos, la dependencia está a la vista y las pruebas
no necesitan infraestructura.

### DEC-16 — El administrador inicial se crea en código, no en una migración

`DataBootstrapper` crea la organización y el administrador **solo si no existe ninguno**.

**Por qué no una migración de Flyway**: una contraseña escrita en un fichero versionado es
una contraseña pública — quedaría en el repositorio, en el historial de git y en cualquier
copia del proyecto. Desde código puede llegar por variable de entorno.

**Por qué "solo si no existe ninguno"**: no es un cargador de datos de ejemplo. En una
instalación con usuarios no hace nada, así que **reiniciar no puede restablecer una
contraseña ya cambiada**.

---

## 4. Decisiones de seguridad que conviene señalar

### 4.1 Los tres fallos de acceso son indistinguibles

Correo inexistente, contraseña incorrecta y cuenta dada de baja producen **el mismo mensaje**.
Distinguirlos convertiría el formulario en un medio de averiguar qué direcciones están
registradas. Lo fija la prueba `allFailuresLookIdentical`.

### 4.2 Defensa frente a enumeración por tiempo de respuesta

Cuando el correo no existe, `AuthenticationService` **verifica igualmente contra un hash de
referencia** y descarta el resultado.

Sin esto, un correo desconocido respondería en microsegundos —no hay hash que comprobar—
mientras que uno registrado tardaría lo que tarda BCrypt, que es deliberadamente lento. Esa
diferencia, medida a escala, permite enumerar direcciones **sin acertar ni una contraseña**.
Lo fija `unknownEmailStillVerifiesAHash`.

El orden de las comprobaciones también importa: el estado de la cuenta se verifica **después**
de la contraseña, para que una cuenta desactivada no se delate por responder antes.

### 4.3 Las contraseñas no pueden filtrarse por un `toString`

`LoginRequest`, `CreateUserRequest`, `ChangePasswordRequest`, `AuthenticationCommand`,
`CreateUserCommand`, `ChangePasswordCommand`, `AuthenticationResult`, `User` y
`UserJpaEntity` **redefinen `toString` para enmascarar** contraseñas, hashes y tokens.

El `toString` generado por omisión de un `record` los imprimiría, y basta con que uno de
estos objetos acabe en el mensaje de una excepción o en una traza de depuración para
filtrarlos. Lo fija `toStringNeverLeaksPasswordHash`.

### 4.4 `UserResponse` no puede exponer el hash

Es una clase distinta del agregado, así que para filtrar el hash **habría que añadirlo ahí a
propósito**. Serializando el dominio directamente, bastaría con olvidar una anotación de
exclusión.

### 4.5 Los errores de los filtros tienen el mismo formato que el resto

`@RestControllerAdvice` solo intercepta lo que ocurre dentro de un controlador, y los filtros
de seguridad se ejecutan antes de que exista ninguno. Sin `SecurityErrorResponder`, un token
caducado produciría la página HTML del contenedor justo donde el cliente más necesita
entender qué pasó.

### 4.6 La clave de firma se valida al arrancar

`JwtProperties` rechaza una clave de menos de 32 caracteres y **la aplicación no arranca**.
Por debajo de 256 bits, HMAC-SHA256 se vuelve atacable por fuerza bruta, y con la clave
comprometida cualquiera podría emitir tokens válidos para cualquier usuario y cualquier rol.

### 4.7 Se cierra por omisión

La regla final es `anyRequest().authenticated()`. Un endpoint nuevo **nace protegido**. La
alternativa —abrir por omisión y cerrar lo sensible— convierte cada olvido en una fuga.

---

## 5. Reglas de negocio implementadas

| Regla | Dónde | Comprobación |
|---|---|---|
| RN-12 — el administrador ve todas las sucursales | `RoleCode.canOperateOnAnyBranch`, `User.canOperateOnBranch` | `adminOperatesOnAnyBranch` |
| RN-13 — el gerente opera en la suya | `User.canOperateOnBranch` | `otherRolesOperateOnlyOnTheirBranch` |
| RF-04 — coherencia rol/sucursal | `User.requireBranchConsistentWithRole` | 7 pruebas en `RoleBranchConsistency` |
| RF-03 — nunca sin administrador | `UserService.requireAnotherActiveAdminExists` | 5 pruebas en `LastAdminProtection` |
| RNF-03 — contraseñas cifradas | `PasswordHasherPort` + BCrypt | `createsUserHashingPassword` |

### El invariante del último administrador

Ninguna restricción de la base puede expresarlo: **la organización nunca se queda sin un
administrador activo**. Sin él, nadie podría gestionar usuarios ni sucursales y habría que
intervenir la base de datos a mano.

Se cierran **los dos caminos** que llevan al mismo sitio: dar de baja y degradar de rol.
Cerrar solo uno dejaría el otro abierto.

Detalle de implementación: el conteo incluye al propio usuario, que aún está activo en ese
punto, de ahí que el umbral sea `<= 1` y no `== 0`.

---

## 6. El uso de `@EntityGraph`

`UserJpaEntity` es el primer sitio donde aparece una asociación real (`@ManyToOne` hacia
`RoleJpaEntity`), y por tanto el primer riesgo de N+1.

**Por qué el rol sí es asociación y la organización no** — la regla general (DEC-07) es
referenciar por identificador entre agregados, y así siguen `organizationId` y `branchId`. El
rol es la excepción por dos motivos:

1. No es un agregado con ciclo de vida propio, sino un **catálogo cerrado de tres filas**.
2. **Siempre se necesita junto al usuario**: sin el rol no se decide nada sobre autorización.

Se declara `LAZY` y se trae con `@EntityGraph` en cada consulta, en lugar de `EAGER`. Con
carga ansiosa, Hibernate decide por su cuenta y en un listado de cien usuarios puede acabar
emitiendo **ciento una consultas**. Con el grafo, la unión es explícita y el listado se
resuelve siempre en una.

Detalle: se redefine `findAll(Specification, Pageable)` de `JpaSpecificationExecutor`
únicamente para poder anotarlo — la versión heredada no admite el grafo, y sin ella el
listado paginado volvería a caer en el N+1.

---

## 7. Endpoints

### Públicos

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Autenticarse y obtener los tokens |
| `POST` | `/api/v1/auth/refresh` | Renovar la sesión |

También abiertos: `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/health`.

### Protegidos

| Método | Ruta | Autorización |
|---|---|---|
| `GET` | `/api/v1/auth/me` | Autenticado |
| `POST` | `/api/v1/organizations/{id}/users` | `ADMIN` + misma organización |
| `GET` | `/api/v1/organizations/{id}/users` | `ADMIN` o `BRANCH_MANAGER` + misma organización |
| `GET` | `/api/v1/users/{id}` | Uno mismo, o `ADMIN`/`BRANCH_MANAGER` |
| `PUT` | `/api/v1/users/{id}/profile` | Uno mismo o `ADMIN` |
| `PUT` | `/api/v1/users/{id}/assignment` | `ADMIN` |
| `POST` | `/api/v1/users/{id}/password` | **Solo uno mismo** |
| `POST` | `/api/v1/users/{id}/deactivation` | `ADMIN` |
| `POST` | `/api/v1/users/{id}/activation` | `ADMIN` |

**Por qué la contraseña solo la cambia uno mismo**, ni siquiera el administrador: restablecer
una cuenta ajena fijando una clave que el administrador conocería es un flujo distinto, y
peor. Además, exigir la contraseña actual protege frente a que un token robado baste para
apoderarse de la cuenta.

---

## 8. Cómo probarlo

```bash
docker compose up -d
./gradlew bootRun
```

Al arrancar por primera vez se crea el administrador y aparece un aviso destacado en el log.

```bash
# 1. Autenticarse
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@optiplant.local","password":"ChangeMe!2026"}'

# 2. Usar el token
TOKEN="<accessToken de la respuesta>"
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"

# 3. Comprobar que sin token no se pasa (debe devolver 401)
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/auth/me
```

Documentación interactiva en `http://localhost:8080/swagger-ui.html`.

> **Antes de exponer esto fuera de un entorno local**, definir `JWT_SECRET` y
> `BOOTSTRAP_ADMIN_PASSWORD`. Con los valores por defecto —ambos públicos, están en este
> repositorio— cualquiera puede entrar como administrador y, peor, emitir tokens válidos
> para cualquier usuario y cualquier rol sin necesidad de credenciales.

---

## 9. Estado de las pruebas

| Clase | Pruebas | Docker |
|---|---|---|
| `BranchTest` | 27 | No |
| `UserTest` | 27 | No |
| `BranchServiceTest` | 13 | No |
| `AuthenticationServiceTest` | 13 | No |
| `UserServiceTest` | 13 | No |
| `InventoriesApplicationTests` | 1 | **Sí** |

**93 pruebas unitarias, todas en verde.**

`InventoriesApplicationTests` ejecuta Flyway contra un PostgreSQL real y, con
`ddl-auto: validate`, comprueba que `UserJpaEntity` y `RoleJpaEntity` cuadran con el esquema.
Requiere Docker en marcha.

---

## 10. Siguiente paso

Con la seguridad resuelta, ya existe el sujeto que todo movimiento de inventario debe
registrar como responsable (RN-11), que era el requisito previo para el núcleo del dominio.

1. **Catálogo** — `Category`, `Product`, `UnitOfMeasure`, `ProductUnit`. El inventario
   referencia productos y unidades, así que va antes. *(`ProductUnit` se retiró en la fase 6:
   el producto lleva una sola unidad y las presentaciones son variantes. Ver
   `PHASE6-CATALOGO-VARIANTES.md`.)*
2. **Inventario y movimientos** — el corazón del sistema: *el stock nunca cambia sin un
   movimiento que lo explique* (RN-04).
3. **Compras, ventas y transferencias**, todas apoyadas en ese mismo mecanismo.

Construir primero el motor de movimientos y montar compras, ventas y transferencias encima
evita tres implementaciones distintas de la misma regla — y tres oportunidades de que una de
ellas la incumpla.
