-- Query-Registry: Externalisierte SQL-Statements
CREATE TABLE IF NOT EXISTS ts_query (
    id          SERIAL PRIMARY KEY,
    query_key   VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    sql_text    TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ts_query_key ON ts_query (query_key);

COMMENT ON TABLE ts_query IS 'Externalisierte SQL-Statements, geladen aus XML beim Deploy, zur Laufzeit aus DB gelesen';
COMMENT ON COLUMN ts_query.query_key IS 'Eindeutiger Schluessel, z.B. businesspartner/overview';
COMMENT ON COLUMN ts_query.name IS 'Lesbarer Name fuer Dokumentation';
COMMENT ON COLUMN ts_query.sql_text IS 'Das SQL-Statement (Basis-Query ohne dynamische WHERE)';
