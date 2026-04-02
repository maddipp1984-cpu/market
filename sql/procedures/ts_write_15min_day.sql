-- Schreibt einen Tag (Upsert). Validiert Array-Laenge gegen DST.
-- Aktualisiert first_date/last_date in ts_header.
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
        RAISE EXCEPTION 'DST-Fehler: Erwartet % Werte fuer %, erhalten %',
            v_expected, p_date, v_actual;
    END IF;

    INSERT INTO ts_values_15min (ts_id, ts_date, vals)
    VALUES (p_ts_id, p_date, p_values)
    ON CONFLICT (ts_id, ts_date) DO UPDATE SET vals = EXCLUDED.vals;

    UPDATE ts_header SET
        first_date = LEAST(first_date, p_date),
        last_date  = GREATEST(last_date, p_date)
    WHERE ts_id = p_ts_id;
END;
$$ LANGUAGE plpgsql;
