-- =====================================================================================
-- V1 — Esquema base del sistema de inventario multi-sucursal
--
-- Deriva de docs/ENTITIES.md (modelo E-R normalizado hasta 3FN).
--
-- Dos desviaciones deliberadas respecto de ese documento, ambas justificadas aqui:
--
--   1. Catalogos de estado hibridos. Los ciclos de vida que gobierna el codigo
--      (estados de venta, compra y transferencia, tipos de movimiento, prioridad,
--      tipos y resoluciones de incidencia, tipos de alerta) se modelan como
--      VARCHAR + CHECK en lugar de tabla + FK. Motivo: son conjuntos cerrados cuyas
--      transiciones ya viven en el dominio como enum; una tabla anadiria un JOIN por
--      consulta y una fuente de verdad duplicada que el codigo tendria que validar
--      igualmente. ENTITIES.md §13.2 admite explicitamente el enum, y §17.2 ya usa
--      CHECK para el estado de alerta. Los catalogos que el negocio SI extiende en
--      ejecucion —unidades de medida, transportistas, rutas, listas de precios—
--      siguen siendo tablas reales.
--
--   2. inventory_movement usa FK especificas (purchase_order_id, sale_id, transfer_id,
--      adjustment_id) en lugar de la pareja polimorfica reference_type/reference_id.
--      Es la opcion que ENTITIES.md §8.5 recomienda por integridad referencial.
--      Un CHECK garantiza que como mucho una de ellas sea no nula.
--
-- Convenciones:
--   * Identificadores UUID (ENTITIES.md §3).
--   * Cantidades e importes en DECIMAL, jamas en punto flotante (DBD-05, DBD-06).
--   * Marcas de tiempo en TIMESTAMPTZ, normalizadas a UTC por la aplicacion.
--   * Maestros con borrado logico via `active`; transaccionales nunca se borran (DBD-04).
--   * `user` y `role` son palabras reservadas en PostgreSQL: se usan app_user y app_role.
-- =====================================================================================

-- =====================================================================================
-- 1. DOMINIO ORGANIZACIONAL
-- =====================================================================================

CREATE TABLE organization (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    legal_name  VARCHAR(200),
    tax_id      VARCHAR(50),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_organization_code   UNIQUE (code),
    CONSTRAINT uq_organization_tax_id UNIQUE (tax_id),
    CONSTRAINT ck_organization_code   CHECK (code <> '')
);

COMMENT ON TABLE organization IS
    'Empresa propietaria de las sucursales. Modelada explicitamente para no codificar '
    'una unica empresa implicita en el resto del esquema (ENTITIES.md §5.1).';

CREATE TABLE branch (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    address_line    VARCHAR(250),
    city            VARCHAR(100),
    country_code    CHAR(2),
    phone           VARCHAR(30),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_branch_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE RESTRICT,
    -- El codigo de sucursal es unico dentro de su organizacion, no globalmente.
    CONSTRAINT uq_branch_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_branch_code     CHECK (code <> '')
);

CREATE INDEX ix_branch_organization ON branch (organization_id);

-- =====================================================================================
-- 2. SEGURIDAD (EP-01, RF-01..RF-04)
-- =====================================================================================

CREATE TABLE app_role (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(250),
    CONSTRAINT uq_app_role_code UNIQUE (code),
    CONSTRAINT ck_app_role_code CHECK (code IN ('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR'))
);

CREATE TABLE app_user (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL,
    -- Nulo para el administrador general, que no pertenece a ninguna sucursal (RN-12).
    branch_id       UUID,
    role_id         UUID         NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_app_user_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_user_branch FOREIGN KEY (branch_id)
        REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_user_role FOREIGN KEY (role_id)
        REFERENCES app_role (id) ON DELETE RESTRICT,
    CONSTRAINT uq_app_user_org_email UNIQUE (organization_id, email),
    CONSTRAINT ck_app_user_email     CHECK (email <> '' AND email LIKE '%@%')
);

CREATE INDEX ix_app_user_branch ON app_user (branch_id);
CREATE INDEX ix_app_user_role   ON app_user (role_id);
-- Sostiene la busqueda por credenciales en el login sin depender del indice compuesto.
CREATE INDEX ix_app_user_email  ON app_user (email);

COMMENT ON COLUMN app_user.password_hash IS
    'Hash BCrypt. La contrasena en claro jamas se persiste ni se registra (RNF-03).';

-- =====================================================================================
-- 3. CATALOGO DE PRODUCTOS (EP-03, RF-07..RF-09)
-- =====================================================================================

CREATE TABLE category (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(250),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_category_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE RESTRICT,
    CONSTRAINT uq_category_org_code UNIQUE (organization_id, code)
);

CREATE INDEX ix_category_organization ON category (organization_id);

-- El producto es global a la organizacion y NO almacena stock: el stock depende
-- simultaneamente de producto y sucursal, y vive en `inventory` (RN-02, ENTITIES.md §7.2).
CREATE TABLE product (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL,
    category_id     UUID,
    sku             VARCHAR(60)  NOT NULL,
    barcode         VARCHAR(100),
    name            VARCHAR(180) NOT NULL,
    description     TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_product_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id)
        REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT uq_product_org_sku UNIQUE (organization_id, sku),
    CONSTRAINT ck_product_sku     CHECK (sku <> '')
);

CREATE INDEX ix_product_organization ON product (organization_id);
CREATE INDEX ix_product_category     ON product (category_id);
CREATE INDEX ix_product_name         ON product (name);
-- El codigo de barras solo se exige unico cuando existe.
CREATE UNIQUE INDEX ux_product_org_barcode
    ON product (organization_id, barcode) WHERE barcode IS NOT NULL;

-- Catalogo global de unidades. Es tabla y no enum porque el negocio anade unidades
-- nuevas en ejecucion sin desplegar codigo.
CREATE TABLE unit_of_measure (
    id     UUID        PRIMARY KEY,
    code   VARCHAR(20) NOT NULL,
    name   VARCHAR(80) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    CONSTRAINT uq_unit_of_measure_code UNIQUE (code)
);

-- Resuelve Product N:M UnitOfMeasure y sostiene RF-09 (multiples unidades por producto).
CREATE TABLE product_unit (
    id                UUID          PRIMARY KEY,
    product_id        UUID          NOT NULL,
    unit_id           UUID          NOT NULL,
    -- Cuantas unidades base representa una unidad de esta presentacion. Ej: 1 caja = 24.
    conversion_factor DECIMAL(18,6) NOT NULL,
    is_base_unit      BOOLEAN       NOT NULL DEFAULT FALSE,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_product_unit_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_unit_unit FOREIGN KEY (unit_id)
        REFERENCES unit_of_measure (id) ON DELETE RESTRICT,
    CONSTRAINT uq_product_unit          UNIQUE (product_id, unit_id),
    CONSTRAINT ck_product_unit_factor   CHECK (conversion_factor > 0),
    -- La unidad base es, por definicion, aquella cuyo factor de conversion es 1.
    CONSTRAINT ck_product_unit_base_factor
        CHECK (NOT is_base_unit OR conversion_factor = 1)
);

CREATE INDEX ix_product_unit_product ON product_unit (product_id);
CREATE INDEX ix_product_unit_unit    ON product_unit (unit_id);
-- Exactamente una unidad base activa por producto (ENTITIES.md §7.4).
CREATE UNIQUE INDEX ux_product_unit_single_base
    ON product_unit (product_id) WHERE is_base_unit AND active;

-- =====================================================================================
-- 4. INVENTARIO (EP-04, RF-10..RF-16)
-- =====================================================================================

-- Saldo actual de un producto en una sucursal. Es una proyeccion persistida:
-- la fuente auditable del cambio es inventory_movement (DBD-02).
CREATE TABLE inventory (
    id            UUID          PRIMARY KEY,
    branch_id     UUID          NOT NULL,
    product_id    UUID          NOT NULL,
    quantity      DECIMAL(18,6) NOT NULL DEFAULT 0,
    minimum_stock DECIMAL(18,6) NOT NULL DEFAULT 0,
    -- Costo promedio ponderado, recalculado en cada entrada por compra (RF-23, S-11).
    average_cost  DECIMAL(18,4) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ   NOT NULL,
    -- Bloqueo optimista: protege el saldo frente a operaciones concurrentes (RNF-05).
    version       INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_branch FOREIGN KEY (branch_id)
        REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE RESTRICT,
    -- Impide duplicar el saldo de un mismo producto dentro de una sucursal (RN-02).
    CONSTRAINT uq_inventory_branch_product UNIQUE (branch_id, product_id),
    CONSTRAINT ck_inventory_quantity      CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_minimum_stock CHECK (minimum_stock >= 0),
    CONSTRAINT ck_inventory_average_cost  CHECK (average_cost >= 0),
    CONSTRAINT ck_inventory_version       CHECK (version >= 0)
);

CREATE INDEX ix_inventory_branch  ON inventory (branch_id);
CREATE INDEX ix_inventory_product ON inventory (product_id);
-- Sostiene la consulta de productos proximos a agotarse (RF-16, HU-40).
CREATE INDEX ix_inventory_low_stock
    ON inventory (branch_id) WHERE quantity <= minimum_stock;

-- Ajuste de inventario como operacion formal, para que toda correccion manual
-- quede respaldada por un documento con responsable y motivo (Flujo D, RNF-12).
CREATE TABLE inventory_adjustment (
    id          UUID          PRIMARY KEY,
    branch_id   UUID          NOT NULL,
    created_by  UUID          NOT NULL,
    approved_by UUID,
    reason      VARCHAR(250)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    approved_at TIMESTAMPTZ,
    CONSTRAINT fk_inventory_adjustment_branch FOREIGN KEY (branch_id)
        REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_adjustment_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_adjustment_approved_by FOREIGN KEY (approved_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_adjustment_reason CHECK (reason <> ''),
    CONSTRAINT ck_inventory_adjustment_approval
        CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
    CONSTRAINT ck_inventory_adjustment_approved_after
        CHECK (approved_at IS NULL OR approved_at >= created_at)
);

CREATE INDEX ix_inventory_adjustment_branch ON inventory_adjustment (branch_id, created_at);

CREATE TABLE inventory_adjustment_item (
    id             UUID          PRIMARY KEY,
    adjustment_id  UUID          NOT NULL,
    product_id     UUID          NOT NULL,
    -- Con signo: positivo entra, negativo sale (ENTITIES.md §18.2).
    quantity_delta DECIMAL(18,6) NOT NULL,
    reason         VARCHAR(250),
    CONSTRAINT fk_inventory_adjustment_item_adjustment FOREIGN KEY (adjustment_id)
        REFERENCES inventory_adjustment (id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_adjustment_item_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT uq_inventory_adjustment_item UNIQUE (adjustment_id, product_id),
    CONSTRAINT ck_inventory_adjustment_item_delta CHECK (quantity_delta <> 0)
);

CREATE INDEX ix_inventory_adjustment_item_adjustment
    ON inventory_adjustment_item (adjustment_id);

-- Bitacora inmutable de cada variacion de stock. Es el nucleo de integridad del
-- dominio: ningun saldo cambia sin una fila aqui que lo explique (RN-04, DBD-03).
CREATE TABLE inventory_movement (
    id                UUID          PRIMARY KEY,
    inventory_id      UUID          NOT NULL,
    movement_type     VARCHAR(20)   NOT NULL,
    user_id           UUID          NOT NULL,
    -- Siempre positiva. El sentido lo aporta movement_type, lo que evita que un mismo
    -- cambio pueda expresarse de dos formas contradictorias (ENTITIES.md §8.4).
    quantity          DECIMAL(18,6) NOT NULL,
    unit_cost         DECIMAL(18,4),
    reason            VARCHAR(250)  NOT NULL,
    -- Documento que origino el movimiento. Como mucho uno, y nunca mas de uno.
    purchase_order_id UUID,
    sale_id           UUID,
    transfer_id       UUID,
    adjustment_id     UUID,
    occurred_at       TIMESTAMPTZ   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_inventory_movement_inventory FOREIGN KEY (inventory_id)
        REFERENCES inventory (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movement_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_movement_adjustment FOREIGN KEY (adjustment_id)
        REFERENCES inventory_adjustment (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_movement_quantity  CHECK (quantity > 0),
    CONSTRAINT ck_inventory_movement_unit_cost CHECK (unit_cost IS NULL OR unit_cost >= 0),
    CONSTRAINT ck_inventory_movement_reason    CHECK (reason <> ''),
    CONSTRAINT ck_inventory_movement_type CHECK (movement_type IN (
        'PURCHASE_IN', 'SALE_OUT', 'TRANSFER_IN', 'TRANSFER_OUT',
        'RETURN_IN', 'LOSS_OUT', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT')),
    CONSTRAINT ck_inventory_movement_single_reference CHECK (
        (CASE WHEN purchase_order_id IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN sale_id           IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN transfer_id       IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN adjustment_id     IS NOT NULL THEN 1 ELSE 0 END) <= 1)
);

-- Indice principal del historico: movimientos de un saldo ordenados en el tiempo (HU-14).
CREATE INDEX ix_inventory_movement_inventory_date
    ON inventory_movement (inventory_id, occurred_at DESC);
CREATE INDEX ix_inventory_movement_user       ON inventory_movement (user_id);
CREATE INDEX ix_inventory_movement_type       ON inventory_movement (movement_type);
CREATE INDEX ix_inventory_movement_occurred   ON inventory_movement (occurred_at DESC);

COMMENT ON TABLE inventory_movement IS
    'Historico inmutable. Un error se corrige con un movimiento de ajuste nuevo, '
    'nunca modificando ni borrando una fila existente (RNF-12, RN-11).';

-- =====================================================================================
-- 5. ALERTAS DE REABASTECIMIENTO (RF-16, HU-16 — funcionalidad adicional)
-- =====================================================================================

CREATE TABLE inventory_alert (
    id                 UUID          PRIMARY KEY,
    inventory_id       UUID          NOT NULL,
    alert_type         VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    -- Saldo que tenia el inventario en el instante en que se disparo la alerta.
    triggered_quantity DECIMAL(18,6) NOT NULL,
    minimum_stock      DECIMAL(18,6) NOT NULL,
    message            VARCHAR(500),
    created_at         TIMESTAMPTZ   NOT NULL,
    resolved_at        TIMESTAMPTZ,
    CONSTRAINT fk_inventory_alert_inventory FOREIGN KEY (inventory_id)
        REFERENCES inventory (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_alert_type
        CHECK (alert_type IN ('LOW_STOCK', 'OUT_OF_STOCK', 'OVERSTOCK')),
    CONSTRAINT ck_inventory_alert_status
        CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_inventory_alert_quantities
        CHECK (triggered_quantity >= 0 AND minimum_stock >= 0),
    -- Una alerta abierta no tiene fecha de cierre; una cerrada siempre la tiene.
    CONSTRAINT ck_inventory_alert_resolution
        CHECK ((status = 'OPEN') = (resolved_at IS NULL)),
    CONSTRAINT ck_inventory_alert_resolved_after
        CHECK (resolved_at IS NULL OR resolved_at >= created_at)
);

CREATE INDEX ix_inventory_alert_inventory ON inventory_alert (inventory_id, created_at DESC);
CREATE INDEX ix_inventory_alert_status    ON inventory_alert (status);
-- Impide acumular alertas abiertas duplicadas del mismo tipo sobre un mismo saldo.
CREATE UNIQUE INDEX ux_inventory_alert_open
    ON inventory_alert (inventory_id, alert_type) WHERE status = 'OPEN';

-- =====================================================================================
-- 6. PROVEEDORES Y COMPRAS (EP-05, RF-17..RF-23)
-- =====================================================================================

CREATE TABLE supplier (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(180) NOT NULL,
    tax_id          VARCHAR(50),
    email           VARCHAR(254),
    phone           VARCHAR(30),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_supplier_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE RESTRICT,
    CONSTRAINT uq_supplier_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_supplier_email    CHECK (email IS NULL OR email LIKE '%@%')
);

CREATE INDEX ix_supplier_organization ON supplier (organization_id);
CREATE UNIQUE INDEX ux_supplier_org_tax_id
    ON supplier (organization_id, tax_id) WHERE tax_id IS NOT NULL;

CREATE TABLE purchase_order (
    id                UUID         PRIMARY KEY,
    branch_id         UUID         NOT NULL,
    supplier_id       UUID         NOT NULL,
    created_by        UUID         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    order_number      VARCHAR(40)  NOT NULL,
    order_date        DATE         NOT NULL,
    -- Condicion comercial: dias de plazo de pago acordados (RF-20, HU-18).
    payment_term_days INTEGER      NOT NULL DEFAULT 0,
    notes             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_purchase_order_branch FOREIGN KEY (branch_id)
        REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_supplier FOREIGN KEY (supplier_id)
        REFERENCES supplier (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT uq_purchase_order_number UNIQUE (branch_id, order_number),
    CONSTRAINT ck_purchase_order_payment_term CHECK (payment_term_days >= 0),
    CONSTRAINT ck_purchase_order_status CHECK (status IN (
        'DRAFT', 'CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'))
);

-- Sostiene el historico de compras por sucursal y por proveedor (RF-22, HU-20).
CREATE INDEX ix_purchase_order_branch   ON purchase_order (branch_id, order_date DESC);
CREATE INDEX ix_purchase_order_supplier ON purchase_order (supplier_id, order_date DESC);
CREATE INDEX ix_purchase_order_status   ON purchase_order (status);

CREATE TABLE purchase_order_item (
    id                  UUID          PRIMARY KEY,
    purchase_order_id   UUID          NOT NULL,
    product_id          UUID          NOT NULL,
    product_unit_id     UUID          NOT NULL,
    quantity            DECIMAL(18,6) NOT NULL,
    received_quantity   DECIMAL(18,6) NOT NULL DEFAULT 0,
    -- Precio pactado en el momento de la compra. Se conserva aunque el catalogo
    -- cambie despues: es el precio de ESTA operacion, no el vigente (DBD-09).
    unit_price          DECIMAL(18,4) NOT NULL,
    discount_percentage DECIMAL(5,2)  NOT NULL DEFAULT 0,
    CONSTRAINT fk_purchase_order_item_order FOREIGN KEY (purchase_order_id)
        REFERENCES purchase_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_order_item_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_item_unit FOREIGN KEY (product_unit_id)
        REFERENCES product_unit (id) ON DELETE RESTRICT,
    CONSTRAINT uq_purchase_order_item UNIQUE (purchase_order_id, product_id, product_unit_id),
    CONSTRAINT ck_purchase_order_item_quantity   CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_item_received   CHECK (received_quantity >= 0),
    CONSTRAINT ck_purchase_order_item_over_receipt CHECK (received_quantity <= quantity),
    CONSTRAINT ck_purchase_order_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_purchase_order_item_discount
        CHECK (discount_percentage BETWEEN 0 AND 100)
);

CREATE INDEX ix_purchase_order_item_order   ON purchase_order_item (purchase_order_id);
CREATE INDEX ix_purchase_order_item_product ON purchase_order_item (product_id);

ALTER TABLE inventory_movement
    ADD CONSTRAINT fk_inventory_movement_purchase_order FOREIGN KEY (purchase_order_id)
        REFERENCES purchase_order (id) ON DELETE RESTRICT;

-- =====================================================================================
-- 7. LISTAS DE PRECIOS (RF-29, HU-25)
-- =====================================================================================

CREATE TABLE price_list (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(250),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    valid_from      DATE,
    valid_until     DATE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_price_list_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE RESTRICT,
    CONSTRAINT uq_price_list_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_price_list_validity CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE INDEX ix_price_list_organization ON price_list (organization_id);

CREATE TABLE product_price (
    id              UUID          PRIMARY KEY,
    price_list_id   UUID          NOT NULL,
    product_id      UUID          NOT NULL,
    product_unit_id UUID          NOT NULL,
    price           DECIMAL(18,4) NOT NULL,
    CONSTRAINT fk_product_price_list FOREIGN KEY (price_list_id)
        REFERENCES price_list (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_price_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_price_unit FOREIGN KEY (product_unit_id)
        REFERENCES product_unit (id) ON DELETE RESTRICT,
    CONSTRAINT uq_product_price UNIQUE (price_list_id, product_id, product_unit_id),
    CONSTRAINT ck_product_price CHECK (price >= 0)
);

CREATE INDEX ix_product_price_list    ON product_price (price_list_id);
CREATE INDEX ix_product_price_product ON product_price (product_id);

-- =====================================================================================
-- 8. VENTAS (EP-06, RF-24..RF-30)
-- =====================================================================================

CREATE TABLE sale (
    id            UUID         PRIMARY KEY,
    branch_id     UUID         NOT NULL,
    created_by    UUID         NOT NULL,
    price_list_id UUID,
    status        VARCHAR(20)  NOT NULL,
    sale_number   VARCHAR(40)  NOT NULL,
    sale_date     TIMESTAMPTZ  NOT NULL,
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_sale_branch FOREIGN KEY (branch_id)
        REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_price_list FOREIGN KEY (price_list_id)
        REFERENCES price_list (id) ON DELETE RESTRICT,
    CONSTRAINT uq_sale_number UNIQUE (branch_id, sale_number),
    CONSTRAINT ck_sale_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED'))
);

-- Indice clave del dashboard: ventas del mes por sucursal (RF-42, HU-38).
CREATE INDEX ix_sale_branch_date ON sale (branch_id, sale_date DESC);
CREATE INDEX ix_sale_status      ON sale (status);
CREATE INDEX ix_sale_created_by  ON sale (created_by);

CREATE TABLE sale_item (
    id                  UUID          PRIMARY KEY,
    sale_id             UUID          NOT NULL,
    product_id          UUID          NOT NULL,
    product_unit_id     UUID          NOT NULL,
    quantity            DECIMAL(18,6) NOT NULL,
    -- Snapshot del precio aplicado. Si manana la lista sube, esta venta historica
    -- debe seguir mostrando el precio al que realmente se vendio (ENTITIES.md §29).
    unit_price          DECIMAL(18,4) NOT NULL,
    discount_percentage DECIMAL(5,2)  NOT NULL DEFAULT 0,
    CONSTRAINT fk_sale_item_sale FOREIGN KEY (sale_id)
        REFERENCES sale (id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_item_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_item_unit FOREIGN KEY (product_unit_id)
        REFERENCES product_unit (id) ON DELETE RESTRICT,
    CONSTRAINT uq_sale_item UNIQUE (sale_id, product_id, product_unit_id),
    CONSTRAINT ck_sale_item_quantity   CHECK (quantity > 0),
    CONSTRAINT ck_sale_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_sale_item_discount   CHECK (discount_percentage BETWEEN 0 AND 100)
);

CREATE INDEX ix_sale_item_sale    ON sale_item (sale_id);
-- Sostiene el analisis de rotacion y productos de mayor demanda (RF-44, HU-39).
CREATE INDEX ix_sale_item_product ON sale_item (product_id);

ALTER TABLE inventory_movement
    ADD CONSTRAINT fk_inventory_movement_sale FOREIGN KEY (sale_id)
        REFERENCES sale (id) ON DELETE RESTRICT;

-- =====================================================================================
-- 9. LOGISTICA (EP-08, RF-35..RF-37)
-- =====================================================================================

CREATE TABLE carrier (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    phone           VARCHAR(30),
    email           VARCHAR(254),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_carrier_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE RESTRICT,
    CONSTRAINT uq_carrier_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_carrier_email    CHECK (email IS NULL OR email LIKE '%@%')
);

CREATE INDEX ix_carrier_organization ON carrier (organization_id);

CREATE TABLE logistics_route (
    id                         UUID          PRIMARY KEY,
    organization_id            UUID          NOT NULL,
    origin_branch_id           UUID          NOT NULL,
    destination_branch_id      UUID          NOT NULL,
    name                       VARCHAR(150),
    estimated_duration_minutes INTEGER       NOT NULL,
    estimated_cost             DECIMAL(18,4),
    priority                   SMALLINT      NOT NULL DEFAULT 0,
    active                     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMPTZ   NOT NULL,
    updated_at                 TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_logistics_route_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE RESTRICT,
    CONSTRAINT fk_logistics_route_origin FOREIGN KEY (origin_branch_id)
        REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_logistics_route_destination FOREIGN KEY (destination_branch_id)
        REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT uq_logistics_route UNIQUE (origin_branch_id, destination_branch_id),
    CONSTRAINT ck_logistics_route_distinct_branches
        CHECK (origin_branch_id <> destination_branch_id),
    CONSTRAINT ck_logistics_route_duration CHECK (estimated_duration_minutes > 0),
    CONSTRAINT ck_logistics_route_cost
        CHECK (estimated_cost IS NULL OR estimated_cost >= 0)
);

CREATE INDEX ix_logistics_route_origin      ON logistics_route (origin_branch_id);
CREATE INDEX ix_logistics_route_destination ON logistics_route (destination_branch_id);

-- =====================================================================================
-- 10. TRANSFERENCIAS ENTRE SUCURSALES (EP-07, RF-31..RF-41)
-- =====================================================================================

CREATE TABLE transfer (
    id                    UUID         PRIMARY KEY,
    transfer_number       VARCHAR(40)  NOT NULL,
    origin_branch_id      UUID         NOT NULL,
    destination_branch_id UUID         NOT NULL,
    requested_by          UUID         NOT NULL,
    approved_by           UUID,
    status                VARCHAR(20)  NOT NULL,
    priority              VARCHAR(10)  NOT NULL,
    carrier_id            UUID,
    route_id              UUID,
    requested_at          TIMESTAMPTZ  NOT NULL,
    approved_at           TIMESTAMPTZ,
    shipped_at            TIMESTAMPTZ,
    estimated_arrival_at  TIMESTAMPTZ,
    received_at           TIMESTAMPTZ,
    notes                 TEXT,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_transfer_origin FOREIGN KEY (origin_branch_id)
        REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_destination FOREIGN KEY (destination_branch_id)
        REFERENCES branch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_requested_by FOREIGN KEY (requested_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_approved_by FOREIGN KEY (approved_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_carrier FOREIGN KEY (carrier_id)
        REFERENCES carrier (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_route FOREIGN KEY (route_id)
        REFERENCES logistics_route (id) ON DELETE RESTRICT,
    CONSTRAINT uq_transfer_number UNIQUE (transfer_number),
    -- RN-07: una transferencia siempre involucra dos sucursales distintas.
    CONSTRAINT ck_transfer_distinct_branches
        CHECK (origin_branch_id <> destination_branch_id),
    CONSTRAINT ck_transfer_status CHECK (status IN (
        'REQUESTED', 'APPROVED', 'IN_PREPARATION', 'IN_TRANSIT',
        'PARTIALLY_RECEIVED', 'RECEIVED', 'ISSUE_PENDING', 'CANCELLED', 'CLOSED')),
    CONSTRAINT ck_transfer_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    -- La linea temporal del traslado no puede ir hacia atras (ENTITIES.md §13.4).
    CONSTRAINT ck_transfer_approved_after
        CHECK (approved_at IS NULL OR approved_at >= requested_at),
    CONSTRAINT ck_transfer_shipped_after
        CHECK (shipped_at IS NULL OR approved_at IS NULL OR shipped_at >= approved_at),
    CONSTRAINT ck_transfer_received_after
        CHECK (received_at IS NULL OR shipped_at IS NULL OR received_at >= shipped_at),
    CONSTRAINT ck_transfer_approval_pair
        CHECK ((approved_by IS NULL) = (approved_at IS NULL))
);

-- Transferencias en curso vistas desde cada extremo (RF-46, HU-35, HU-41).
CREATE INDEX ix_transfer_origin_status      ON transfer (origin_branch_id, status);
CREATE INDEX ix_transfer_destination_status ON transfer (destination_branch_id, status);
CREATE INDEX ix_transfer_status             ON transfer (status);
CREATE INDEX ix_transfer_requested_at       ON transfer (requested_at DESC);
CREATE INDEX ix_transfer_carrier            ON transfer (carrier_id);
CREATE INDEX ix_transfer_route              ON transfer (route_id);

ALTER TABLE inventory_movement
    ADD CONSTRAINT fk_inventory_movement_transfer FOREIGN KEY (transfer_id)
        REFERENCES transfer (id) ON DELETE RESTRICT;

CREATE TABLE transfer_item (
    id                 UUID          PRIMARY KEY,
    transfer_id        UUID          NOT NULL,
    product_id         UUID          NOT NULL,
    product_unit_id    UUID          NOT NULL,
    requested_quantity DECIMAL(18,6) NOT NULL,
    approved_quantity  DECIMAL(18,6),
    shipped_quantity   DECIMAL(18,6),
    received_quantity  DECIMAL(18,6),
    CONSTRAINT fk_transfer_item_transfer FOREIGN KEY (transfer_id)
        REFERENCES transfer (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_item_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_item_unit FOREIGN KEY (product_unit_id)
        REFERENCES product_unit (id) ON DELETE RESTRICT,
    CONSTRAINT uq_transfer_item UNIQUE (transfer_id, product_id, product_unit_id),
    CONSTRAINT ck_transfer_item_requested CHECK (requested_quantity > 0),
    CONSTRAINT ck_transfer_item_approved
        CHECK (approved_quantity IS NULL
               OR (approved_quantity >= 0 AND approved_quantity <= requested_quantity)),
    CONSTRAINT ck_transfer_item_shipped
        CHECK (shipped_quantity IS NULL OR shipped_quantity >= 0),
    CONSTRAINT ck_transfer_item_shipped_within_approved
        CHECK (approved_quantity IS NULL OR shipped_quantity IS NULL
               OR shipped_quantity <= approved_quantity),
    CONSTRAINT ck_transfer_item_received
        CHECK (received_quantity IS NULL OR received_quantity >= 0),
    CONSTRAINT ck_transfer_item_received_within_shipped
        CHECK (shipped_quantity IS NULL OR received_quantity IS NULL
               OR received_quantity <= shipped_quantity)
);

CREATE INDEX ix_transfer_item_transfer ON transfer_item (transfer_id);
CREATE INDEX ix_transfer_item_product  ON transfer_item (product_id);

COMMENT ON TABLE transfer_item IS
    'La cantidad faltante NO se persiste: se deriva de shipped_quantity - received_quantity. '
    'Persistirla permitiria estados contradictorios (DBD-08, ENTITIES.md §13.6).';

-- Historico de estados. El estado vigente vive en transfer.status; aqui vive como
-- se llego hasta el, que es lo que sostiene los indicadores logisticos (HU-36, HU-37).
CREATE TABLE transfer_status_history (
    id          UUID         PRIMARY KEY,
    transfer_id UUID         NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    changed_by  UUID         NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL,
    notes       VARCHAR(500),
    CONSTRAINT fk_transfer_status_history_transfer FOREIGN KEY (transfer_id)
        REFERENCES transfer (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_status_history_user FOREIGN KEY (changed_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_transfer_status_history_status CHECK (status IN (
        'REQUESTED', 'APPROVED', 'IN_PREPARATION', 'IN_TRANSIT',
        'PARTIALLY_RECEIVED', 'RECEIVED', 'ISSUE_PENDING', 'CANCELLED', 'CLOSED'))
);

CREATE INDEX ix_transfer_status_history_transfer
    ON transfer_status_history (transfer_id, changed_at);

-- Faltantes y averias. Se conservan como incidencia y nunca se resuelven
-- manipulando el inventario destino (RN-10, ENTITIES.md §37).
CREATE TABLE transfer_issue (
    id               UUID          PRIMARY KEY,
    transfer_item_id UUID          NOT NULL,
    issue_type       VARCHAR(20)   NOT NULL,
    resolution_type  VARCHAR(20),
    quantity         DECIMAL(18,6) NOT NULL,
    description      VARCHAR(500),
    reported_by      UUID          NOT NULL,
    reported_at      TIMESTAMPTZ   NOT NULL,
    resolved_by      UUID,
    resolved_at      TIMESTAMPTZ,
    CONSTRAINT fk_transfer_issue_item FOREIGN KEY (transfer_item_id)
        REFERENCES transfer_item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_issue_reported_by FOREIGN KEY (reported_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_issue_resolved_by FOREIGN KEY (resolved_by)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_transfer_issue_quantity CHECK (quantity > 0),
    CONSTRAINT ck_transfer_issue_type
        CHECK (issue_type IN ('MISSING', 'DAMAGED', 'WRONG_PRODUCT', 'OTHER')),
    CONSTRAINT ck_transfer_issue_resolution_type
        CHECK (resolution_type IS NULL
               OR resolution_type IN ('RESHIPMENT', 'ADJUSTMENT', 'CLAIM')),
    -- Una incidencia resuelta necesita responsable, fecha y tipo de resolucion (HU-33).
    CONSTRAINT ck_transfer_issue_resolution_complete CHECK (
        (resolved_at IS NULL AND resolved_by IS NULL AND resolution_type IS NULL) OR
        (resolved_at IS NOT NULL AND resolved_by IS NOT NULL AND resolution_type IS NOT NULL)),
    CONSTRAINT ck_transfer_issue_resolved_after
        CHECK (resolved_at IS NULL OR resolved_at >= reported_at)
);

CREATE INDEX ix_transfer_issue_item     ON transfer_issue (transfer_item_id);
CREATE INDEX ix_transfer_issue_reported ON transfer_issue (reported_at DESC);
-- Bandeja de incidencias pendientes de resolver.
CREATE INDEX ix_transfer_issue_unresolved
    ON transfer_issue (transfer_item_id) WHERE resolved_at IS NULL;
