-- V3: Soft-delete em orders, products e users.
--     Adiciona coluna deleted_at + índices parciais.
--     Substitui constraints unique de users por índices parciais (permite reuso após exclusão).

-- Coluna deleted_at
ALTER TABLE orders   ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;
ALTER TABLE products ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;
ALTER TABLE users    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

-- Índices parciais para acelerar filtro "ativos" (deleted_at IS NULL)
CREATE INDEX IF NOT EXISTS idx_orders_active_deleted_at
    ON orders (deleted_at) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_products_active_deleted_at
    ON products (deleted_at) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_active_deleted_at
    ON users (deleted_at) WHERE deleted_at IS NULL;

-- Substitui unique constraints globais de users por índices parciais (reuso após soft-delete)
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_email;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_tenant_name;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_active
    ON users (email) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_tenant_name_active
    ON users (customer_saas_id, name) WHERE deleted_at IS NULL;
