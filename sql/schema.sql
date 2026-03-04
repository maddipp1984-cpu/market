-- ============================================================
-- Zeitreihensystem - Datenbankschema (Horizontales Modell)
-- TimescaleDB / PostgreSQL
-- ============================================================
-- Horizontales Modell: 1 Zeile pro Tag, Werte als Array
-- DST-Handling: Array-Länge variiert (92/96/100 für 1/4h)
-- ============================================================

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- ============================================================
-- 1. Header-Tabelle (Metadaten)
-- ============================================================
CREATE TABLE ts_header (
    ts_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ts_key        VARCHAR(255) NOT NULL UNIQUE,
    time_dim      SMALLINT NOT NULL,
    unit          VARCHAR(50),
    timezone      VARCHAR(50) NOT NULL DEFAULT 'Europe/Berlin',
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_time_dim CHECK (time_dim IN (1, 2, 3, 4, 5))
);

COMMENT ON COLUMN ts_header.time_dim IS '1=15min, 2=1h, 3=Tag, 4=Monat, 5=Jahr';
CREATE INDEX idx_header_time_dim ON ts_header (time_dim);

-- ============================================================
-- 2. Viertelstundenwerte (horizontal: 1 Zeile/Tag, 92-100 Werte)
-- ============================================================
CREATE TABLE ts_values_15min (
    ts_id    BIGINT NOT NULL REFERENCES ts_header(ts_id),
    ts_date  DATE NOT NULL,
    vals     DOUBLE PRECISION[] NOT NULL
);

SELECT create_hypertable('ts_values_15min', by_range('ts_date', INTERVAL '1 year'));
SELECT add_dimension('ts_values_15min', by_hash('ts_id', 8));
CREATE UNIQUE INDEX idx_15min_pk ON ts_values_15min (ts_id, ts_date);

ALTER TABLE ts_values_15min SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_15min', INTERVAL '3 months');

-- ============================================================
-- 3. Stundenwerte (horizontal: 1 Zeile/Tag, 23-25 Werte)
-- ============================================================
CREATE TABLE ts_values_1h (
    ts_id    BIGINT NOT NULL REFERENCES ts_header(ts_id),
    ts_date  DATE NOT NULL,
    vals     DOUBLE PRECISION[] NOT NULL
);

SELECT create_hypertable('ts_values_1h', by_range('ts_date', INTERVAL '1 year'));
SELECT add_dimension('ts_values_1h', by_hash('ts_id', 4));
CREATE UNIQUE INDEX idx_1h_pk ON ts_values_1h (ts_id, ts_date);

ALTER TABLE ts_values_1h SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_1h', INTERVAL '6 months');

-- ============================================================
-- 4. Tageswerte (einfach: 1 Zeile/Tag, 1 Wert)
-- ============================================================
CREATE TABLE ts_values_day (
    ts_id    BIGINT NOT NULL REFERENCES ts_header(ts_id),
    ts_date  DATE NOT NULL,
    value    DOUBLE PRECISION
);

SELECT create_hypertable('ts_values_day', by_range('ts_date', INTERVAL '1 year'));
SELECT add_dimension('ts_values_day', by_hash('ts_id', 4));
CREATE UNIQUE INDEX idx_day_pk ON ts_values_day (ts_id, ts_date);

ALTER TABLE ts_values_day SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_day', INTERVAL '2 years');

-- ============================================================
-- 5. Monatswerte
-- ============================================================
CREATE TABLE ts_values_month (
    ts_id    BIGINT NOT NULL REFERENCES ts_header(ts_id),
    ts_date  DATE NOT NULL,
    value    DOUBLE PRECISION
);

SELECT create_hypertable('ts_values_month', by_range('ts_date', INTERVAL '5 years'));
CREATE UNIQUE INDEX idx_month_pk ON ts_values_month (ts_id, ts_date);

ALTER TABLE ts_values_month SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_month', INTERVAL '5 years');

-- ============================================================
-- 6. Jahreswerte
-- ============================================================
CREATE TABLE ts_values_year (
    ts_id    BIGINT NOT NULL REFERENCES ts_header(ts_id),
    ts_year  SMALLINT NOT NULL,
    value    DOUBLE PRECISION,
    PRIMARY KEY (ts_id, ts_year)
);
