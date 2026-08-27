-- =====================================================================================
-- V2 — Datos de referencia
--
-- Solo entra aqui lo que el sistema necesita para arrancar y que no puede crear un
-- usuario desde la API: los tres roles del modelo de actores y el catalogo base de
-- unidades de medida. Los datos de demostracion (organizacion, sucursales, productos)
-- NO viven en una migracion: se cargan aparte para no ensuciar una base productiva.
--
-- Los UUID son fijos y no aleatorios para que el mismo identificador signifique lo
-- mismo en cualquier entorno, y para poder referenciarlos desde los tests.
-- =====================================================================================

INSERT INTO app_role (id, code, name, description) VALUES
    ('11111111-0000-4000-8000-000000000001', 'ADMIN', 'Administrador general',
     'Visibilidad y operacion sobre toda la organizacion y todas sus sucursales (RN-12).'),
    ('11111111-0000-4000-8000-000000000002', 'BRANCH_MANAGER', 'Gerente de sucursal',
     'Responsable operativo de una sucursal: supervisa, aprueba transferencias y consulta indicadores (RN-13).'),
    ('11111111-0000-4000-8000-000000000003', 'INVENTORY_OPERATOR', 'Operador de inventario',
     'Ejecuta las operaciones diarias: compras, ventas, entradas, salidas y transferencias.');

INSERT INTO unit_of_measure (id, code, name, symbol) VALUES
    ('22222222-0000-4000-8000-000000000001', 'UNIT',    'Unidad',     'und'),
    ('22222222-0000-4000-8000-000000000002', 'KG',      'Kilogramo',  'kg'),
    ('22222222-0000-4000-8000-000000000003', 'G',       'Gramo',      'g'),
    ('22222222-0000-4000-8000-000000000004', 'L',       'Litro',      'L'),
    ('22222222-0000-4000-8000-000000000005', 'ML',      'Mililitro',  'ml'),
    ('22222222-0000-4000-8000-000000000006', 'M',       'Metro',      'm'),
    ('22222222-0000-4000-8000-000000000007', 'BOX',     'Caja',       'caja'),
    ('22222222-0000-4000-8000-000000000008', 'PACK',    'Paquete',    'paq'),
    ('22222222-0000-4000-8000-000000000009', 'PALLET',  'Pallet',     'plt');
