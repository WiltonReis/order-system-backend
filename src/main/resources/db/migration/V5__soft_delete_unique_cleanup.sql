-- V5: Cleanup defensivo de UNIQUE constraints/índices globais em users.
--
-- Motivação: V3 declarou DROP CONSTRAINT IF EXISTS por nome (uk_users_email,
-- uk_users_tenant_name). Se o schema inicial foi criado pelo ddl-auto:update
-- (antes da adoção do Flyway) ou por um baseline antigo, as constraints podem
-- ter nomes auto-gerados pelo Hibernate (ex.: UK_6dotkott...). Nesse caso, V3
-- pulou silenciosamente e o UNIQUE global continuou ativo — bloqueando reuso
-- de email/nome de usuário soft-deletado.
--
-- Esta migration usa introspecção do catálogo do PostgreSQL (pg_constraint /
-- pg_index) para localizar e remover qualquer UNIQUE constraint ou índice
-- único global remanescente em users, INDEPENDENTE do nome. Apenas índices
-- únicos parciais (deleted_at IS NULL) — alinhados ao soft-delete — devem
-- restar controlando a unicidade.
--
-- Robustez: a filtragem da PRIMARY KEY usa pg_index.indisprimary em vez de
-- depender de nomes específicos (users_pkey, pk_users, etc.), evitando o erro
-- "cannot drop index <pk> because constraint <pk> on table users requires it".

DO $$
DECLARE
    rec record;
BEGIN
    -- 1) Remove TODAS as UNIQUE table constraints em users (contype = 'u').
    --    A entidade User não declara @UniqueConstraint, então removê-las é
    --    seguro contra o ddl-auto:validate do Hibernate. Constraints de
    --    PRIMARY KEY (contype = 'p') e FOREIGN KEY (contype = 'f') ficam
    --    intactas por construção do filtro.
    FOR rec IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'users'::regclass
          AND contype = 'u'
    LOOP
        EXECUTE 'ALTER TABLE users DROP CONSTRAINT ' || quote_ident(rec.conname);
    END LOOP;

    -- 2) Remove índices únicos NÃO-parciais em users que NÃO sejam PRIMARY KEY.
    --    Filtros via pg_index (sem depender de nomes):
    --      - i.indisunique = true   → apenas índices únicos
    --      - i.indisprimary = false → exclui o índice da PK (qualquer nome)
    --      - i.indpred IS NULL      → exclui índices parciais (mantém os
    --                                  uk_users_email_active e
    --                                  uk_users_tenant_name_active criados em V3)
    FOR rec IN
        SELECT c.relname AS indexname
        FROM pg_index i
        JOIN pg_class  c ON c.oid = i.indexrelid
        JOIN pg_class  t ON t.oid = i.indrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE t.relname = 'users'
          AND n.nspname = current_schema()
          AND i.indisunique = true
          AND i.indisprimary = false
          AND i.indpred IS NULL
    LOOP
        EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(rec.indexname);
    END LOOP;
END $$;

-- 3) (Re)criar os índices únicos parciais — idempotente. Se V3 já criou,
--    é no-op; se algum nunca chegou a existir, é criado agora.
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_active
    ON users (email)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_tenant_name_active
    ON users (customer_saas_id, name)
    WHERE deleted_at IS NULL;
