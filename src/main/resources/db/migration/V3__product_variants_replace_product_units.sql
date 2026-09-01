-- =====================================================================================
-- V3 — Las variantes sustituyen a las presentaciones con factor de conversion
--
-- Que se va: `product_unit`, la tabla que resolvia Product N:M UnitOfMeasure con un
-- `conversion_factor`. Con ella, el stock se guardaba en "unidades base" y cada linea de
-- venta, compra o transferencia tenia que traducirse multiplicando por un factor. Esa
-- traduccion es la que hacia opaco el catalogo: el usuario no veia cuantas botellas hay,
-- veia un numero que habia que convertir.
--
-- Que llega:
--   * `product.unit_id` — cada producto se cuenta en UNA unidad, la suya, sin factor.
--   * `product.parent_product_id` — las presentaciones distintas del mismo articulo
--     pasan a ser productos completos (variantes) colgados de un principal. Tienen SKU,
--     stock, movimientos y precio propios; el enlace es solo agrupacion de catalogo.
--
-- `inventory.quantity` NO se toca: ya estaba expresado en la unidad base, que es la que
-- el producto conserva. El invariante RN-04 (todo cambio de stock respaldado por un
-- movimiento) queda intacto, porque esta migracion no mueve stock.
--
-- Las lineas historicas SI se normalizan: una linea que decia "2 cajas" con factor 24
-- pasa a decir "48" en la unidad del producto, y su precio unitario se divide por 24
-- para que el importe de la linea siga siendo el mismo. Se corrige el dato, no el
-- importe.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. Nuevas columnas de producto
-- -------------------------------------------------------------------------------------
ALTER TABLE product
    ADD COLUMN unit_id           UUID,
    ADD COLUMN parent_product_id UUID;

-- La unidad del producto es la que era su base. Si algun producto quedo sin base activa,
-- se toma cualquier presentacion suya; si tampoco tiene, la unidad generica.
UPDATE product p
   SET unit_id = COALESCE(
        (SELECT pu.unit_id FROM product_unit pu
          WHERE pu.product_id = p.id AND pu.is_base_unit AND pu.active LIMIT 1),
        (SELECT pu.unit_id FROM product_unit pu
          WHERE pu.product_id = p.id ORDER BY pu.is_base_unit DESC LIMIT 1),
        '22222222-0000-4000-8000-000000000001');

ALTER TABLE product
    ALTER COLUMN unit_id SET NOT NULL,
    ADD CONSTRAINT fk_product_unit FOREIGN KEY (unit_id)
        REFERENCES unit_of_measure (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_product_parent FOREIGN KEY (parent_product_id)
        REFERENCES product (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_product_parent_not_self CHECK (parent_product_id <> id);

CREATE INDEX ix_product_parent ON product (parent_product_id)
    WHERE parent_product_id IS NOT NULL;

COMMENT ON COLUMN product.unit_id IS
    'Unidad en la que se cuenta el stock de este producto. Sin factor de conversion: '
    'el saldo son unidades de esta unidad, no un numero a traducir.';

COMMENT ON COLUMN product.parent_product_id IS
    'Producto principal del que este es variante. Solo agrupa el catalogo: la variante '
    'tiene inventario, movimientos y precio propios.';

-- El catalogo es de un solo nivel: una variante no puede tener variantes. No es
-- expresable con un CHECK, porque exige mirar otra fila.
CREATE OR REPLACE FUNCTION product_parent_must_be_principal() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.parent_product_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM product parent
                    WHERE parent.id = NEW.parent_product_id
                      AND parent.parent_product_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Una variante no puede tener variantes propias (producto %).', NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_product_parent_must_be_principal
    BEFORE INSERT OR UPDATE OF parent_product_id ON product
    FOR EACH ROW EXECUTE FUNCTION product_parent_must_be_principal();

-- -------------------------------------------------------------------------------------
-- 2. Normalizacion de las lineas historicas a la unidad del producto
-- -------------------------------------------------------------------------------------
UPDATE sale_item si
   SET quantity   = si.quantity * pu.conversion_factor,
       unit_price = si.unit_price / pu.conversion_factor
  FROM product_unit pu
 WHERE pu.id = si.product_unit_id
   AND pu.conversion_factor <> 1;

UPDATE purchase_order_item poi
   SET quantity          = poi.quantity * pu.conversion_factor,
       received_quantity = poi.received_quantity * pu.conversion_factor,
       unit_price        = poi.unit_price / pu.conversion_factor
  FROM product_unit pu
 WHERE pu.id = poi.product_unit_id
   AND pu.conversion_factor <> 1;

UPDATE transfer_item ti
   SET requested_quantity = ti.requested_quantity * pu.conversion_factor,
       approved_quantity  = ti.approved_quantity * pu.conversion_factor,
       shipped_quantity   = ti.shipped_quantity * pu.conversion_factor,
       received_quantity  = ti.received_quantity * pu.conversion_factor
  FROM product_unit pu
 WHERE pu.id = ti.product_unit_id
   AND pu.conversion_factor <> 1;

UPDATE product_price pp
   SET price = pp.price / pu.conversion_factor
  FROM product_unit pu
 WHERE pu.id = pp.product_unit_id
   AND pu.conversion_factor <> 1;

-- -------------------------------------------------------------------------------------
-- 3. Fusion de lineas duplicadas
--
-- Un documento podia tener dos lineas del mismo producto en presentaciones distintas.
-- Normalizadas, ambas hablan de lo mismo, asi que se suman en una sola: la cantidad se
-- acumula y el precio unitario pasa a ser el promedio ponderado por cantidad, para que
-- el importe total del documento no cambie.
-- -------------------------------------------------------------------------------------
WITH merged AS (
    SELECT sale_id,
           product_id,
           MIN(id::text)::uuid                                    AS survivor_id,
           SUM(quantity)                                          AS quantity,
           SUM(quantity * unit_price) / SUM(quantity)             AS unit_price,
           SUM(quantity * discount_percentage) / SUM(quantity)    AS discount_percentage
      FROM sale_item
     GROUP BY sale_id, product_id
    HAVING COUNT(*) > 1
)
UPDATE sale_item si
   SET quantity            = m.quantity,
       unit_price          = m.unit_price,
       discount_percentage = m.discount_percentage
  FROM merged m
 WHERE si.id = m.survivor_id;

DELETE FROM sale_item si
 WHERE si.id <> (SELECT MIN(other.id::text)::uuid FROM sale_item other
                  WHERE other.sale_id = si.sale_id AND other.product_id = si.product_id);

WITH merged AS (
    SELECT purchase_order_id,
           product_id,
           MIN(id::text)::uuid                                    AS survivor_id,
           SUM(quantity)                                          AS quantity,
           SUM(received_quantity)                                 AS received_quantity,
           SUM(quantity * unit_price) / SUM(quantity)             AS unit_price,
           SUM(quantity * discount_percentage) / SUM(quantity)    AS discount_percentage
      FROM purchase_order_item
     GROUP BY purchase_order_id, product_id
    HAVING COUNT(*) > 1
)
UPDATE purchase_order_item poi
   SET quantity            = m.quantity,
       received_quantity   = m.received_quantity,
       unit_price          = m.unit_price,
       discount_percentage = m.discount_percentage
  FROM merged m
 WHERE poi.id = m.survivor_id;

DELETE FROM purchase_order_item poi
 WHERE poi.id <> (SELECT MIN(other.id::text)::uuid FROM purchase_order_item other
                   WHERE other.purchase_order_id = poi.purchase_order_id
                     AND other.product_id = poi.product_id);

WITH merged AS (
    SELECT transfer_id,
           product_id,
           MIN(id::text)::uuid        AS survivor_id,
           SUM(requested_quantity)    AS requested_quantity,
           SUM(approved_quantity)     AS approved_quantity,
           SUM(shipped_quantity)      AS shipped_quantity,
           SUM(received_quantity)     AS received_quantity
      FROM transfer_item
     GROUP BY transfer_id, product_id
    HAVING COUNT(*) > 1
)
UPDATE transfer_item ti
   SET requested_quantity = m.requested_quantity,
       approved_quantity  = m.approved_quantity,
       shipped_quantity   = m.shipped_quantity,
       received_quantity  = m.received_quantity
  FROM merged m
 WHERE ti.id = m.survivor_id;

DELETE FROM transfer_item ti
 WHERE ti.id <> (SELECT MIN(other.id::text)::uuid FROM transfer_item other
                  WHERE other.transfer_id = ti.transfer_id AND other.product_id = ti.product_id);

-- En los precios no se promedia: dos precios del mismo producto en la misma lista son
-- una contradiccion, no una suma. Sobrevive el de la presentacion que era base, que es
-- el que ya estaba expresado en la unidad del producto.
DELETE FROM product_price pp
 WHERE pp.id <> (
        SELECT other.id
          FROM product_price other
          JOIN product_unit pu ON pu.id = other.product_unit_id
         WHERE other.price_list_id = pp.price_list_id
           AND other.product_id = pp.product_id
         ORDER BY pu.is_base_unit DESC, other.id
         LIMIT 1);

-- -------------------------------------------------------------------------------------
-- 4. Retirada de product_unit
-- -------------------------------------------------------------------------------------
ALTER TABLE sale_item
    DROP CONSTRAINT fk_sale_item_unit,
    DROP CONSTRAINT uq_sale_item,
    DROP COLUMN product_unit_id,
    ADD CONSTRAINT uq_sale_item UNIQUE (sale_id, product_id);

ALTER TABLE purchase_order_item
    DROP CONSTRAINT fk_purchase_order_item_unit,
    DROP CONSTRAINT uq_purchase_order_item,
    DROP COLUMN product_unit_id,
    ADD CONSTRAINT uq_purchase_order_item UNIQUE (purchase_order_id, product_id);

ALTER TABLE transfer_item
    DROP CONSTRAINT fk_transfer_item_unit,
    DROP CONSTRAINT uq_transfer_item,
    DROP COLUMN product_unit_id,
    ADD CONSTRAINT uq_transfer_item UNIQUE (transfer_id, product_id);

ALTER TABLE product_price
    DROP CONSTRAINT fk_product_price_unit,
    DROP CONSTRAINT uq_product_price,
    DROP COLUMN product_unit_id,
    ADD CONSTRAINT uq_product_price UNIQUE (price_list_id, product_id);

DROP TABLE product_unit;
