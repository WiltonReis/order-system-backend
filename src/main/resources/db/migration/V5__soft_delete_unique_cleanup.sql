-- V5: Cleanup defensivo de UNIQUE constraints/índices globais em users.
--
-- Motivação: V3 declarou DROP CONSTRAINT IF EXISTS por nome (uk_users_email,
-- uk_users_tenant_name). Se o schema inicial foi criado pelo ddl-auto:update
-- (antes da adoção do Flyway) ou por um baseline antigo, as constraints podem
-- ter nomes auto-gerados pelo Hibernate (ex.: UK_6dotkott...). Nesse caso, V3
-- pulou silenciosamente e o UNIQUE global continuou ativo — bloqueando reuso
-- de email/nome de usuário soft-deletado.
--
-- Esta migration remove por introspecção qualquer UNIQUE constraint ou índice
-- único global remanescente em users, garantindo que apenas os índices únicos
-- parciais (deleted_at IS NULL) — alinhados ao soft-delete — controlem a
-- unicidade.

DO $$
DECLARE
    rec record;
BEGIN
    -- 1) Remove TODAS as UNIQUE table constraints em users.
    --    A entidade User não declara @UniqueConstraint, então removê-las é
    --    seguro contra o ddl-auto:validate do Hibernate.
    FOR rec IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'users'::regclass
          AND contype = 'u'
    LOOP
        EXECUTE 'ALTER TABLE users DROP CONSTRAINT ' || quote_ident(rec.conname);
    END LOOP;

    -- 2) Remove índices únicos NÃO-parciais em users (exceto a PK e os
    --    índices parciais alvo). Cobre índices criados via CREATE UNIQUE INDEX
    --    fora de uma constraint, incluindo nomes auto-gerados pelo Hibernate.
    FOR rec IN
        SELECT indexname
        FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'users'
          AND indexdef ILIKE '%UNIQUE%'
          AND indexname NOT IN (
              'users_pkey',
              'uk_users_email_active',
              'uk_users_tenant_name_active'
          )
          AND position(' WHERE ' IN indexdef) = 0
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
