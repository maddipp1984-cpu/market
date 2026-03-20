-- Testdaten: 1000 Zeitreihen pro Dimension mit zufaelligen Werten
-- Laufzeit: zufaellig 1-5 Jahre, Start zwischen 2020 und 2024

-- ============================================================
-- 1. Headers anlegen
-- ============================================================

-- 1000x Viertelstunde (time_dim=1)
INSERT INTO ts_header (ts_key, time_dim, unit_id, description)
SELECT 'QH_' || LPAD(n::text, 5, '0'), 1, 1, 'Test-Viertelstunde ' || n
FROM generate_series(1, 1000) n;

-- 1000x Stunde (time_dim=2)
INSERT INTO ts_header (ts_key, time_dim, unit_id, description)
SELECT 'H_' || LPAD(n::text, 5, '0'), 2, 1, 'Test-Stunde ' || n
FROM generate_series(1, 1000) n;

-- 1000x Tag (time_dim=3)
INSERT INTO ts_header (ts_key, time_dim, unit_id, description)
SELECT 'D_' || LPAD(n::text, 5, '0'), 3, 1, 'Test-Tag ' || n
FROM generate_series(1, 1000) n;

-- 1000x Monat (time_dim=4)
INSERT INTO ts_header (ts_key, time_dim, unit_id, description)
SELECT 'M_' || LPAD(n::text, 5, '0'), 4, 1, 'Test-Monat ' || n
FROM generate_series(1, 1000) n;

-- 1000x Jahr (time_dim=5)
INSERT INTO ts_header (ts_key, time_dim, unit_id, description)
SELECT 'Y_' || LPAD(n::text, 5, '0'), 5, 1, 'Test-Jahr ' || n
FROM generate_series(1, 1000) n;

-- ============================================================
-- 2. Viertelstundenwerte (96 Werte/Tag als Array)
-- ============================================================
DO $$
DECLARE
    rec RECORD;
    start_date DATE;
    end_date DATE;
    dur_years INT;
BEGIN
    RAISE NOTICE 'Starte QH-Werte...';
    FOR rec IN SELECT ts_id FROM ts_header WHERE ts_key LIKE 'QH_%' ORDER BY ts_id LOOP
        dur_years := 1 + floor(random() * 5)::int;
        start_date := '2020-01-01'::date + floor(random() * 1461)::int;
        end_date := start_date + (dur_years * 365);

        INSERT INTO ts_values_15min (ts_id, ts_date, vals)
        SELECT rec.ts_id, d::date,
               ARRAY(SELECT round((random() * 500 + 50)::numeric, 2) FROM generate_series(1, 96))
        FROM generate_series(start_date, end_date, '1 day'::interval) d;

        IF rec.ts_id % 100 = 0 THEN
            RAISE NOTICE 'QH: % headers done', rec.ts_id;
        END IF;
    END LOOP;
    RAISE NOTICE 'QH-Werte fertig.';
END $$;

-- ============================================================
-- 3. Stundenwerte (24 Werte/Tag als Array)
-- ============================================================
DO $$
DECLARE
    rec RECORD;
    start_date DATE;
    end_date DATE;
    dur_years INT;
BEGIN
    RAISE NOTICE 'Starte H-Werte...';
    FOR rec IN SELECT ts_id FROM ts_header WHERE ts_key LIKE 'H_%' ORDER BY ts_id LOOP
        dur_years := 1 + floor(random() * 5)::int;
        start_date := '2020-01-01'::date + floor(random() * 1461)::int;
        end_date := start_date + (dur_years * 365);

        INSERT INTO ts_values_1h (ts_id, ts_date, vals)
        SELECT rec.ts_id, d::date,
               ARRAY(SELECT round((random() * 500 + 50)::numeric, 2) FROM generate_series(1, 24))
        FROM generate_series(start_date, end_date, '1 day'::interval) d;

        IF rec.ts_id % 100 = 0 THEN
            RAISE NOTICE 'H: % headers done', rec.ts_id;
        END IF;
    END LOOP;
    RAISE NOTICE 'H-Werte fertig.';
END $$;

-- ============================================================
-- 4. Tageswerte (1 Wert/Tag)
-- ============================================================
DO $$
DECLARE
    rec RECORD;
    start_date DATE;
    end_date DATE;
    dur_years INT;
BEGIN
    RAISE NOTICE 'Starte D-Werte...';
    FOR rec IN SELECT ts_id FROM ts_header WHERE ts_key LIKE 'D_%' ORDER BY ts_id LOOP
        dur_years := 1 + floor(random() * 5)::int;
        start_date := '2020-01-01'::date + floor(random() * 1461)::int;
        end_date := start_date + (dur_years * 365);

        INSERT INTO ts_values_day (ts_id, ts_date, value)
        SELECT rec.ts_id, d::date, round((random() * 1000)::numeric, 2)
        FROM generate_series(start_date, end_date, '1 day'::interval) d;

        IF rec.ts_id % 100 = 0 THEN
            RAISE NOTICE 'D: % headers done', rec.ts_id;
        END IF;
    END LOOP;
    RAISE NOTICE 'D-Werte fertig.';
END $$;

-- ============================================================
-- 5. Monatswerte (1 Wert/Monat)
-- ============================================================
DO $$
DECLARE
    rec RECORD;
    start_date DATE;
    end_date DATE;
    dur_years INT;
BEGIN
    RAISE NOTICE 'Starte M-Werte...';
    FOR rec IN SELECT ts_id FROM ts_header WHERE ts_key LIKE 'M_%' ORDER BY ts_id LOOP
        dur_years := 1 + floor(random() * 5)::int;
        start_date := date_trunc('month', '2020-01-01'::date + floor(random() * 1461)::int * '1 day'::interval)::date;
        end_date := (start_date + (dur_years || ' years')::interval)::date;

        INSERT INTO ts_values_month (ts_id, ts_date, value)
        SELECT rec.ts_id, d::date, round((random() * 10000)::numeric, 2)
        FROM generate_series(start_date, end_date, '1 month'::interval) d;
    END LOOP;
    RAISE NOTICE 'M-Werte fertig.';
END $$;

-- ============================================================
-- 6. Jahreswerte (1 Wert/Jahr)
-- ============================================================
DO $$
DECLARE
    rec RECORD;
    start_year INT;
    dur_years INT;
BEGIN
    RAISE NOTICE 'Starte Y-Werte...';
    FOR rec IN SELECT ts_id FROM ts_header WHERE ts_key LIKE 'Y_%' ORDER BY ts_id LOOP
        dur_years := 1 + floor(random() * 5)::int;
        start_year := 2020 + floor(random() * 5)::int;

        INSERT INTO ts_values_year (ts_id, ts_year, value)
        SELECT rec.ts_id, y, round((random() * 100000)::numeric, 2)
        FROM generate_series(start_year, start_year + dur_years - 1) y;
    END LOOP;
    RAISE NOTICE 'Y-Werte fertig.';
END $$;

-- Zusammenfassung
SELECT 'Headers' AS was, count(*) AS anzahl FROM ts_header
UNION ALL
SELECT 'QH-Werte', count(*) FROM ts_values_15min
UNION ALL
SELECT 'H-Werte', count(*) FROM ts_values_1h
UNION ALL
SELECT 'D-Werte', count(*) FROM ts_values_day
UNION ALL
SELECT 'M-Werte', count(*) FROM ts_values_month
UNION ALL
SELECT 'Y-Werte', count(*) FROM ts_values_year;
