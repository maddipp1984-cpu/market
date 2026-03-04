-- ============================================================
-- Zeitreihensystem - Datenbankschema (Horizontales Modell)
-- TimescaleDB / PostgreSQL
-- ============================================================
-- Horizontales Modell: 1 Zeile pro Tag, Werte als Array
-- DST-Handling: Array-Länge variiert (92/96/100 für 1/4h)
-- ============================================================

CREATE EXTENSION IF NOT EXISTS timescaledb;

-- ============================================================
-- 1. Referenztabellen
-- ============================================================

-- Einheiten
CREATE TABLE ts_unit (
    unit_id      SMALLINT PRIMARY KEY,
    symbol       VARCHAR(20) NOT NULL UNIQUE,
    description  TEXT
);

INSERT INTO ts_unit (unit_id, symbol, description) VALUES
    -- Energie
    (1,  'kWh',    'Kilowattstunde'),
    (2,  'MWh',    'Megawattstunde'),
    (3,  'GWh',    'Gigawattstunde'),
    (4,  'kJ',     'Kilojoule'),
    (5,  'MJ',     'Megajoule'),
    (6,  'GJ',     'Gigajoule'),
    -- Leistung
    (10, 'W',      'Watt'),
    (11, 'kW',     'Kilowatt'),
    (12, 'MW',     'Megawatt'),
    (13, 'GW',     'Gigawatt'),
    (14, 'kVA',    'Kilovoltampere'),
    (15, 'MVA',    'Megavoltampere'),
    (16, 'kvar',   'Kilovar'),
    (17, 'Mvar',   'Megavar'),
    -- Gas - Volumen
    (20, 'm³',     'Kubikmeter'),
    (21, 'Nm³',    'Normkubikmeter'),
    (22, 'Tm³',    'Tausend Kubikmeter'),
    -- Temperatur / Druck / Physik
    (30, '°C',     'Grad Celsius'),
    (31, 'K',      'Kelvin'),
    (32, 'bar',    'Bar'),
    (33, 'mbar',   'Millibar'),
    (34, '%',      'Prozent'),
    -- Mengen / Sonstiges
    (50, 't',      'Tonne'),
    (51, 'kg',     'Kilogramm'),
    (52, 't CO₂',  'Tonne CO₂'),
    (53, 'h',      'Stunden'),
    (54, '',       'Dimensionslos');

-- Währungen
CREATE TABLE ts_currency (
    currency_id  SMALLINT PRIMARY KEY,
    iso_code     VARCHAR(3) NOT NULL UNIQUE,
    description  TEXT
);

INSERT INTO ts_currency (currency_id, iso_code, description) VALUES
    (1, 'EUR', 'Euro'),
    (2, 'USD', 'US-Dollar'),
    (3, 'GBP', 'Britisches Pfund'),
    (4, 'CHF', 'Schweizer Franken'),
    (5, 'DKK', 'Dänische Krone'),
    (6, 'NOK', 'Norwegische Krone'),
    (7, 'SEK', 'Schwedische Krone'),
    (8, 'PLN', 'Polnischer Zloty'),
    (9, 'CZK', 'Tschechische Krone');

-- Objekttypen
CREATE TABLE ts_object_type (
    type_id      SMALLINT PRIMARY KEY,
    type_key     VARCHAR(50) NOT NULL UNIQUE,
    description  TEXT
);

INSERT INTO ts_object_type (type_id, type_key, description) VALUES
    (1, 'CONTRACT_VHP',    'Vertrag VHP'),
    (2, 'CONTRACT',        'Vertrag'),
    (3, 'CONTRACT_VERANS', 'Vertragsanschluss'),
    (4, 'ANS',             'Anschluss');

-- Objekte
CREATE TABLE ts_object (
    object_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type_id      SMALLINT NOT NULL,
    object_key   VARCHAR(255) NOT NULL UNIQUE,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_object_type FOREIGN KEY (type_id) REFERENCES ts_object_type (type_id)
);

CREATE INDEX idx_object_type ON ts_object (type_id);

-- ============================================================
-- 2. Header-Tabelle (Metadaten)
-- ============================================================
CREATE TABLE ts_header (
    ts_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ts_key        VARCHAR(255) NOT NULL UNIQUE,
    time_dim      SMALLINT NOT NULL,
    unit_id       SMALLINT NOT NULL,
    currency_id   SMALLINT,
    object_id     BIGINT,
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_time_dim CHECK (time_dim IN (1, 2, 3, 4, 5)),
    CONSTRAINT fk_unit FOREIGN KEY (unit_id) REFERENCES ts_unit (unit_id),
    CONSTRAINT fk_currency FOREIGN KEY (currency_id) REFERENCES ts_currency (currency_id),
    CONSTRAINT fk_header_object FOREIGN KEY (object_id) REFERENCES ts_object (object_id)
);

COMMENT ON COLUMN ts_header.time_dim IS '1=15min, 2=1h, 3=Tag, 4=Monat, 5=Jahr';
COMMENT ON COLUMN ts_header.unit_id IS 'Referenz auf ts_unit';
COMMENT ON COLUMN ts_header.currency_id IS 'Nur bei Preiszeitreihen, sonst NULL';
CREATE INDEX idx_header_time_dim ON ts_header (time_dim);
CREATE INDEX idx_header_object ON ts_header (object_id);

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
