-- V2: Adiciona colunas de auditoria (updated_at, version) às entidades de domínio
--     e created_at onde ainda não existe.
--     Adiciona constraint unique (order_id, product_id) em order_items.

-- users: não tinha created_at nem updated_at
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE users ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- products: não tinha created_at nem updated_at
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE products ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE products ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- orders: já tinha created_at; adiciona apenas updated_at e version
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- order_items: não tinha nenhuma coluna de auditoria
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE order_items ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Garante um único produto por pedido
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS uk_order_items_order_product;
ALTER TABLE order_items ADD CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id);
