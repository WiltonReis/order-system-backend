-- V4: Histórico de mudanças de status do pedido.
--     Tabela imutável (insert-only) para auditoria.

CREATE TABLE IF NOT EXISTS order_status_history (
    id                UUID PRIMARY KEY,
    order_id          UUID NOT NULL,
    customer_saas_id  UUID NOT NULL,
    from_status       VARCHAR(20) NOT NULL,
    to_status         VARCHAR(20) NOT NULL,
    changed_at        TIMESTAMP NOT NULL,
    changed_by        VARCHAR(150),
    CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_osh_customer_saas FOREIGN KEY (customer_saas_id) REFERENCES customer_saas (id)
);

CREATE INDEX IF NOT EXISTS idx_osh_order_changed_at
    ON order_status_history (order_id, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_osh_customer_saas_id
    ON order_status_history (customer_saas_id);
