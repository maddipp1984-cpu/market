-- Migration 002: DB-Schema an aktuellen Stand (schema.sql) angleichen
-- - Referenztabellen ts_unit, ts_currency anlegen
-- - ts_header: unit (VARCHAR) → unit_id (SMALLINT FK), timezone entfernen, currency_id ergänzen
-- - Stored Procedures: timezone-Parameter entfernen
--
-- Ausführung: docker exec timeseries-db psql -U postgres -d timeseries -f /tmp/002.sql

BEGIN;

-- ================================================================
-- 1. Referenztabellen anlegen
-- ================================================================

CREATE TABLE ts_unit (
    unit_id      SMALLINT PRIMARY KEY,
    symbol       VARCHAR(20) NOT NULL UNIQUE,
    description  TEXT
);

INSERT INTO ts_unit (unit_id, symbol, description) VALUES
    (1,  'kWh',    'Kilowattstunde'),
    (2,  'MWh',    'Megawattstunde'),
    (3,  'GWh',    'Gigawattstunde'),
    (4,  'kJ',     'Kilojoule'),
    (5,  'MJ',     'Megajoule'),
    (6,  'GJ',     'Gigajoule'),
    (10, 'W',      'Watt'),
    (11, 'kW',     'Kilowatt'),
    (12, 'MW',     'Megawatt'),
    (13, 'GW',     'Gigawatt'),
    (14, 'kVA',    'Kilovoltampere'),
    (15, 'MVA',    'Megavoltampere'),
    (16, 'kvar',   'Kilovar'),
    (17, 'Mvar',   'Megavar'),
    (20, 'm³',     'Kubikmeter'),
    (21, 'Nm³',    'Normkubikmeter'),
    (22, 'Tm³',    'Tausend Kubikmeter'),
    (30, '°C',     'Grad Celsius'),
    (31, 'K',      'Kelvin'),
    (32, 'bar',    'Bar'),
    (33, 'mbar',   'Millibar'),
    (34, '%',      'Prozent'),
    (50, 't',      'Tonne'),
    (51, 'kg',     'Kilogramm'),
    (52, 't CO₂',  'Tonne CO₂'),
    (53, 'h',      'Stunden'),
    (54, '',       'Dimensionslos');

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

-- ================================================================
-- 2. ts_header migrieren: unit (VARCHAR) → unit_id (SMALLINT)
-- ================================================================

-- Neue Spalten anlegen
ALTER TABLE ts_header ADD COLUMN unit_id SMALLINT;
ALTER TABLE ts_header ADD COLUMN currency_id SMALLINT;

-- Bestehende unit-Textwerte in unit_id mappen
UPDATE ts_header h
SET unit_id = u.unit_id
FROM ts_unit u
WHERE u.symbol = h.unit;

-- Sicherheitscheck: Alle gemappt?
DO $$
DECLARE
    v_unmapped INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_unmapped FROM ts_header WHERE unit_id IS NULL;
    IF v_unmapped > 0 THEN
        RAISE EXCEPTION 'Migration abgebrochen: % Header ohne unit_id-Mapping', v_unmapped;
    END IF;
END $$;

-- unit_id NOT NULL setzen + FK
ALTER TABLE ts_header ALTER COLUMN unit_id SET NOT NULL;
ALTER TABLE ts_header ADD CONSTRAINT fk_unit FOREIGN KEY (unit_id) REFERENCES ts_unit (unit_id);
ALTER TABLE ts_header ADD CONSTRAINT fk_currency FOREIGN KEY (currency_id) REFERENCES ts_currency (currency_id);

-- Alte Spalten entfernen
ALTER TABLE ts_header DROP COLUMN unit;
ALTER TABLE ts_header DROP COLUMN timezone;

-- Kommentare
COMMENT ON COLUMN ts_header.time_dim IS '1=15min, 2=1h, 3=Tag, 4=Monat, 5=Jahr';
COMMENT ON COLUMN ts_header.unit_id IS 'Referenz auf ts_unit';
COMMENT ON COLUMN ts_header.currency_id IS 'Nur bei Preiszeitreihen, sonst NULL';

-- ================================================================
-- 3. Stored Procedures: alte Signaturen droppen, neue anlegen
-- ================================================================

-- Alte Hilfsfunktionen (mit timezone-Parameter)
DROP FUNCTION IF EXISTS ts_intervals_per_day(DATE, TEXT, INTERVAL);
DROP FUNCTION IF EXISTS ts_generate_timestamps(DATE, TEXT, INTERVAL);

-- Write/Read (gleiche Signatur, aber Body referenziert alte Funktionen)
DROP FUNCTION IF EXISTS ts_write_15min_day(BIGINT, DATE, DOUBLE PRECISION[]);
DROP FUNCTION IF EXISTS ts_write_15min_year(BIGINT, INTEGER, DOUBLE PRECISION[]);
DROP FUNCTION IF EXISTS ts_write_15min_range(BIGINT, DATE, DATE, DOUBLE PRECISION[]);
DROP FUNCTION IF EXISTS ts_write_1h_day(BIGINT, DATE, DOUBLE PRECISION[]);
DROP FUNCTION IF EXISTS ts_write_1h_year(BIGINT, INTEGER, DOUBLE PRECISION[]);
DROP FUNCTION IF EXISTS ts_read_15min(BIGINT, DATE, DATE);
DROP FUNCTION IF EXISTS ts_read_1h(BIGINT, DATE, DATE);

-- Hilfsfunktionen (ohne timezone)
CREATE OR REPLACE FUNCTION ts_intervals_per_day(
    p_date     DATE,
    p_interval INTERVAL
) RETURNS INTEGER AS $$
    SELECT (EXTRACT(EPOCH FROM (
        (p_date + 1)::TIMESTAMP AT TIME ZONE 'Europe/Berlin'
        - p_date::TIMESTAMP AT TIME ZONE 'Europe/Berlin'
    )) / EXTRACT(EPOCH FROM p_interval))::INTEGER;
$$ LANGUAGE sql IMMUTABLE STRICT;

CREATE OR REPLACE FUNCTION ts_generate_timestamps(
    p_date     DATE,
    p_interval INTERVAL
) RETURNS TIMESTAMPTZ[] AS $$
    SELECT ARRAY(
        SELECT generate_series(
            p_date::TIMESTAMP AT TIME ZONE 'Europe/Berlin',
            (p_date + 1)::TIMESTAMP AT TIME ZONE 'Europe/Berlin' - p_interval,
            p_interval
        )
    );
$$ LANGUAGE sql IMMUTABLE STRICT;

-- ts_write_15min_day
CREATE OR REPLACE FUNCTION ts_write_15min_day(
    p_ts_id  BIGINT,
    p_date   DATE,
    p_values DOUBLE PRECISION[]
) RETURNS VOID AS $$
DECLARE
    v_expected INTEGER;
    v_actual   INTEGER;
BEGIN
    v_expected := ts_intervals_per_day(p_date, INTERVAL '15 minutes');
    v_actual := array_length(p_values, 1);

    IF v_actual != v_expected THEN
        RAISE EXCEPTION 'DST-Fehler: Erwartet % Werte für %, erhalten %',
            v_expected, p_date, v_actual;
    END IF;

    INSERT INTO ts_values_15min (ts_id, ts_date, vals)
    VALUES (p_ts_id, p_date, p_values)
    ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;
END;
$$ LANGUAGE plpgsql;

-- ts_write_15min_year
CREATE OR REPLACE FUNCTION ts_write_15min_year(
    p_ts_id  BIGINT,
    p_year   INTEGER,
    p_values DOUBLE PRECISION[]
) RETURNS INTEGER AS $$
DECLARE
    v_date      DATE;
    v_end_date  DATE;
    v_expected  INTEGER;
    v_offset    INTEGER := 1;
    v_days      INTEGER := 0;
BEGIN
    v_date := make_date(p_year, 1, 1);
    v_end_date := make_date(p_year + 1, 1, 1);

    WHILE v_date < v_end_date LOOP
        v_expected := ts_intervals_per_day(v_date, INTERVAL '15 minutes');

        INSERT INTO ts_values_15min (ts_id, ts_date, vals)
        VALUES (p_ts_id, v_date, p_values[v_offset : v_offset + v_expected - 1])
        ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;

        v_offset := v_offset + v_expected;
        v_date := v_date + 1;
        v_days := v_days + 1;
    END LOOP;

    IF v_offset - 1 != array_length(p_values, 1) THEN
        RAISE EXCEPTION 'Array-Länge stimmt nicht: Erwartet %, erhalten %',
            v_offset - 1, array_length(p_values, 1);
    END IF;

    RETURN v_days;
END;
$$ LANGUAGE plpgsql;

-- ts_write_15min_range
CREATE OR REPLACE FUNCTION ts_write_15min_range(
    p_ts_id  BIGINT,
    p_from   DATE,
    p_to     DATE,
    p_values DOUBLE PRECISION[]
) RETURNS INTEGER AS $$
DECLARE
    v_date      DATE;
    v_expected  INTEGER;
    v_offset    INTEGER := 1;
    v_days      INTEGER := 0;
BEGIN
    v_date := p_from;
    WHILE v_date < p_to LOOP
        v_expected := ts_intervals_per_day(v_date, INTERVAL '15 minutes');

        INSERT INTO ts_values_15min (ts_id, ts_date, vals)
        VALUES (p_ts_id, v_date, p_values[v_offset : v_offset + v_expected - 1])
        ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;

        v_offset := v_offset + v_expected;
        v_date := v_date + 1;
        v_days := v_days + 1;
    END LOOP;

    RETURN v_days;
END;
$$ LANGUAGE plpgsql;

-- ts_write_1h_day
CREATE OR REPLACE FUNCTION ts_write_1h_day(
    p_ts_id  BIGINT,
    p_date   DATE,
    p_values DOUBLE PRECISION[]
) RETURNS VOID AS $$
DECLARE
    v_expected INTEGER;
    v_actual   INTEGER;
BEGIN
    v_expected := ts_intervals_per_day(p_date, INTERVAL '1 hour');
    v_actual := array_length(p_values, 1);

    IF v_actual != v_expected THEN
        RAISE EXCEPTION 'DST-Fehler: Erwartet % Werte für %, erhalten %',
            v_expected, p_date, v_actual;
    END IF;

    INSERT INTO ts_values_1h (ts_id, ts_date, vals)
    VALUES (p_ts_id, p_date, p_values)
    ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;
END;
$$ LANGUAGE plpgsql;

-- ts_write_1h_year
CREATE OR REPLACE FUNCTION ts_write_1h_year(
    p_ts_id  BIGINT,
    p_year   INTEGER,
    p_values DOUBLE PRECISION[]
) RETURNS INTEGER AS $$
DECLARE
    v_date      DATE;
    v_end_date  DATE;
    v_expected  INTEGER;
    v_offset    INTEGER := 1;
    v_days      INTEGER := 0;
BEGIN
    v_date := make_date(p_year, 1, 1);
    v_end_date := make_date(p_year + 1, 1, 1);

    WHILE v_date < v_end_date LOOP
        v_expected := ts_intervals_per_day(v_date, INTERVAL '1 hour');

        INSERT INTO ts_values_1h (ts_id, ts_date, vals)
        VALUES (p_ts_id, v_date, p_values[v_offset : v_offset + v_expected - 1])
        ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;

        v_offset := v_offset + v_expected;
        v_date := v_date + 1;
        v_days := v_days + 1;
    END LOOP;

    IF v_offset - 1 != array_length(p_values, 1) THEN
        RAISE EXCEPTION 'Array-Länge stimmt nicht: Erwartet %, erhalten %',
            v_offset - 1, array_length(p_values, 1);
    END IF;

    RETURN v_days;
END;
$$ LANGUAGE plpgsql;

-- ts_read_15min
CREATE OR REPLACE FUNCTION ts_read_15min(
    p_ts_id  BIGINT,
    p_from   DATE DEFAULT NULL,
    p_to     DATE DEFAULT NULL
) RETURNS TABLE(ts_time TIMESTAMPTZ, value DOUBLE PRECISION) AS $$
BEGIN
    RETURN QUERY
    SELECT
        (d.ts_date::TIMESTAMP AT TIME ZONE 'Europe/Berlin') + ((gs.i - 1) * INTERVAL '15 minutes'),
        d.vals[gs.i]
    FROM ts_values_15min d
    CROSS JOIN LATERAL generate_series(1, array_length(d.vals, 1)) AS gs(i)
    WHERE d.ts_id = p_ts_id
      AND (p_from IS NULL OR d.ts_date >= p_from)
      AND (p_to IS NULL OR d.ts_date < p_to)
    ORDER BY d.ts_date, gs.i;
END;
$$ LANGUAGE plpgsql STABLE;

-- ts_read_1h
CREATE OR REPLACE FUNCTION ts_read_1h(
    p_ts_id  BIGINT,
    p_from   DATE DEFAULT NULL,
    p_to     DATE DEFAULT NULL
) RETURNS TABLE(ts_time TIMESTAMPTZ, value DOUBLE PRECISION) AS $$
BEGIN
    RETURN QUERY
    SELECT
        (d.ts_date::TIMESTAMP AT TIME ZONE 'Europe/Berlin') + ((gs.i - 1) * INTERVAL '1 hour'),
        d.vals[gs.i]
    FROM ts_values_1h d
    CROSS JOIN LATERAL generate_series(1, array_length(d.vals, 1)) AS gs(i)
    WHERE d.ts_id = p_ts_id
      AND (p_from IS NULL OR d.ts_date >= p_from)
      AND (p_to IS NULL OR d.ts_date < p_to)
    ORDER BY d.ts_date, gs.i;
END;
$$ LANGUAGE plpgsql STABLE;

COMMIT;
