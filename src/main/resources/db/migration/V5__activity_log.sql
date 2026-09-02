-- =====================================================================================
-- V5 - Traza centralizada de auditoria (activity_log)
--
-- Registra que se hizo, cuando, quien y con que rol. La escriben los propios casos de
-- uso sin codigo anadido: las clases anotadas con @AuditedUseCase enganchan un manejador
-- de java.util.logging, y cada log.info/warning/severe termina como una fila aqui.
--
-- Decisiones:
--
--   1. Sin claves foraneas hacia app_user ni organization. Es denormalizacion
--      deliberada (el equivalente a lo que ENTITIES.md §8.2 razona para
--      inventory.quantity): el registro debe seguir diciendo quien hizo que aunque el
--      usuario cambie de correo, cambie de rol o se de de baja. Ademas, la fila se
--      escribe en una transaccion propia (REQUIRES_NEW) para sobrevivir al rollback de
--      la operacion auditada, y una FK obligaria a que la fila referenciada estuviese ya
--      confirmada, lo que no siempre es cierto en ese instante.
--
--   2. Solo INSERT y SELECT. No hay UPDATE ni DELETE en el puerto de salida: una traza
--      que se puede reescribir no prueba nada (RNF-12), igual que un movimiento de
--      inventario confirmado no se toca (RN-04).
--
--   3. Columnas actor_role y log_level en lugar de role y level, siguiendo la misma
--      cautela con las palabras reservadas que llevo a app_user y app_role en V1.
-- =====================================================================================

CREATE TABLE activity_log (
    id              UUID          PRIMARY KEY,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    username        VARCHAR(150)  NOT NULL,
    user_id         UUID,
    organization_id UUID,
    actor_role      VARCHAR(30)   NOT NULL,
    use_case        VARCHAR(150)  NOT NULL,
    operation       VARCHAR(1000) NOT NULL,
    log_level       VARCHAR(10)   NOT NULL,
    CONSTRAINT ck_activity_log_username  CHECK (username <> ''),
    CONSTRAINT ck_activity_log_use_case  CHECK (use_case <> ''),
    CONSTRAINT ck_activity_log_operation CHECK (operation <> ''),
    CONSTRAINT ck_activity_log_role      CHECK (actor_role IN
        ('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR', 'SYSTEM')),
    CONSTRAINT ck_activity_log_level     CHECK (log_level IN ('INFO', 'WARNING', 'SEVERE'))
);

COMMENT ON TABLE activity_log IS
    'Traza de auditoria de solo insercion. Una fila por cada registro emitido por un caso '
    'de uso anotado con @AuditedUseCase.';

COMMENT ON COLUMN activity_log.username IS
    'Correo del usuario que provoco la operacion, o ''sistema'' si no habia peticion '
    'autenticada detras (arranque, tareas internas).';

COMMENT ON COLUMN activity_log.actor_role IS
    'Rol vigente del usuario en el momento de la operacion. Se copia, no se resuelve por '
    'FK: si el usuario cambia de rol manana, lo que hizo ayer lo hizo con el de ayer.';

COMMENT ON COLUMN activity_log.use_case IS
    'Caso de uso emisor. Por omision el nombre simple de la clase; @AuditedUseCase admite '
    'un nombre de modulo propio.';

COMMENT ON COLUMN activity_log.operation IS
    'Descripcion de la operacion, tal como la compuso el caso de uso. Se recorta a 1000 '
    'caracteres antes de insertar: un mensaje largo no puede tumbar la operacion que '
    'estaba registrando.';

-- Consulta principal del listado: la traza de una organizacion, de lo mas reciente a lo
-- mas antiguo. Sin este indice, cada pagina obligaria a ordenar la tabla entera.
CREATE INDEX idx_activity_log_organization_occurred
    ON activity_log (organization_id, occurred_at DESC);

-- Filtros secundarios del panel de auditoria.
CREATE INDEX idx_activity_log_username ON activity_log (username);
CREATE INDEX idx_activity_log_use_case ON activity_log (use_case);
