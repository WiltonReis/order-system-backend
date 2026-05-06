-- V1: Baseline schema — estado inicial capturado do ddl-auto: update

CREATE TABLE IF NOT EXISTS customer_saas (
    id              UUID         NOT NULL,
    company_name    VARCHAR(200) NOT NULL,
    cpf_cnpj        VARCHAR(20)  NOT NULL,
    contact_email   VARCHAR(200) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_customer_saas PRIMARY KEY (id),
    CONSTRAINT uk_customer_saas_cpf_cnpj UNIQUE (cpf_cnpj)
);

CREATE INDEX IF NOT EXISTS idx_customer_saas_cpf_cnpj ON customer_saas (cpf_cnpj);

CREATE TABLE IF NOT EXISTS users (
    id                   UUID         NOT NULL,
    email                VARCHAR(200) NOT NULL,
    name                 VARCHAR(150) NOT NULL,
    password             VARCHAR(255) NOT NULL,
    role                 VARCHAR(31)  NOT NULL,
    token_revoked_before TIMESTAMP,
    customer_saas_id     UUID         NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_tenant_name UNIQUE (customer_saas_id, name),
    CONSTRAINT fk_users_customer_saas FOREIGN KEY (customer_saas_id) REFERENCES customer_saas (id)
);

CREATE INDEX IF NOT EXISTS idx_users_customer_saas_id ON users (customer_saas_id);

CREATE TABLE IF NOT EXISTS products (
    id               UUID           NOT NULL,
    name             VARCHAR(255)   NOT NULL,
    description      VARCHAR(200),
    price            NUMERIC(10, 2) NOT NULL,
    image_url        VARCHAR(255),
    created_by_name  VARCHAR(255),
    customer_saas_id UUID           NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT fk_products_customer_saas FOREIGN KEY (customer_saas_id) REFERENCES customer_saas (id)
);

CREATE INDEX IF NOT EXISTS idx_products_customer_saas_id ON products (customer_saas_id);

CREATE TABLE IF NOT EXISTS orders (
    id                UUID           NOT NULL,
    status            VARCHAR(31)    NOT NULL,
    created_at        TIMESTAMP      NOT NULL,
    completed_at      TIMESTAMP,
    canceled_at       TIMESTAMP,
    customer_name     VARCHAR(150),
    total             NUMERIC(10, 2) NOT NULL,
    discount          NUMERIC(10, 2) NOT NULL,
    order_code        VARCHAR(8),
    completed_by_name VARCHAR(255),
    canceled_by_name  VARCHAR(255),
    user_id           UUID           NOT NULL,
    customer_saas_id  UUID           NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_customer_saas_order_code UNIQUE (customer_saas_id, order_code),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_customer_saas FOREIGN KEY (customer_saas_id) REFERENCES customer_saas (id)
);

CREATE INDEX IF NOT EXISTS idx_orders_status ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at);
CREATE INDEX IF NOT EXISTS idx_orders_customer_saas_id ON orders (customer_saas_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer_saas_id_status ON orders (customer_saas_id, status);

CREATE TABLE IF NOT EXISTS order_items (
    id         UUID           NOT NULL,
    order_id   UUID           NOT NULL,
    product_id UUID           NOT NULL,
    quantity   INTEGER        NOT NULL,
    price      NUMERIC(10, 2) NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE IF NOT EXISTS revoked_tokens (
    id         UUID        NOT NULL,
    jti        VARCHAR(36) NOT NULL,
    revoked_at TIMESTAMP   NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    CONSTRAINT pk_revoked_tokens PRIMARY KEY (id),
    CONSTRAINT uk_revoked_tokens_jti UNIQUE (jti)
);

CREATE INDEX IF NOT EXISTS idx_revoked_tokens_jti ON revoked_tokens (jti);
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expires_at ON revoked_tokens (expires_at);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         UUID        NOT NULL,
    token      VARCHAR(36) NOT NULL,
    user_id    UUID        NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
