-- Schreibt einen Stundentag (Upsert). Validiert Array-Länge gegen DST.
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
