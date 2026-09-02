-- V1 baseline added `sale.updated_at NOT NULL` by mistake: ENTITIES.md §11.2 and
-- SaleJpaEntity both design `sale` with only `created_at` — a sale doesn't get "updated" in
-- the usual sense, it changes status, and that's explained by the inventory movements it
-- generates, not by a timestamp. No code writes this column, so every insert violates the
-- NOT NULL constraint.
ALTER TABLE sale DROP COLUMN updated_at;
