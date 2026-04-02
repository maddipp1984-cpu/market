-- Schreibt ein ganzes Jahr Stundenwerte aus einem flachen Array.
-- Aktualisiert first_date/last_date in ts_header.
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
        RAISE EXCEPTION 'Array-Laenge stimmt nicht: Erwartet %, erhalten %',
            v_offset - 1, array_length(p_values, 1);
    END IF;

    UPDATE ts_header SET
        first_date = LEAST(first_date, make_date(p_year, 1, 1)),
        last_date  = GREATEST(last_date, make_date(p_year, 12, 31))
    WHERE ts_id = p_ts_id;

    RETURN v_days;
END;
$$ LANGUAGE plpgsql;
