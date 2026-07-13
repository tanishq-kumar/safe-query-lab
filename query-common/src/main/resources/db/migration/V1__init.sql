-- Single source of truth for the schema. Consumed by:
--   1. Flyway at query-api startup
--   2. the conformance suite's Testcontainers Postgres
--   3. jOOQ code generation (engine-jooq build)
-- so schema drift between codegen, tests and runtime is impossible.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE account (
    id          UUID PRIMARY KEY,
    name        TEXT NOT NULL,
    -- Used by the documented stretch goal (join filter); unused in v1.
    risk_rating TEXT NOT NULL
);

-- Named "transactions" (plural): TRANSACTION is close enough to reserved that
-- the singular would need quoting in five different SQL layers.
CREATE TABLE transactions (
    id           UUID PRIMARY KEY,
    account_id   UUID NOT NULL REFERENCES account (id),
    amount       NUMERIC(19, 4) NOT NULL,
    currency     CHAR(3) NOT NULL,
    -- TEXT + CHECK instead of a native PG enum: a PG enum would need ::casts in
    -- JDBC, a jOOQ converter, a MyBatis TypeHandler and Hibernate @JdbcTypeCode —
    -- noise that buries the point of the comparison (predicate assembly).
    status       TEXT NOT NULL,
    type         TEXT NOT NULL,
    description  TEXT NOT NULL,
    counterparty TEXT, -- nullable on purpose: engines must prove null handling
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_type CHECK (type IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_tx_account ON transactions (account_id);
CREATE INDEX idx_tx_created ON transactions (created_at);
CREATE INDEX idx_tx_status ON transactions (status);
-- Trigram index so ILIKE '%text%' can use an index scan on large tables.
CREATE INDEX idx_tx_desc_trgm ON transactions USING gin (description gin_trgm_ops);
