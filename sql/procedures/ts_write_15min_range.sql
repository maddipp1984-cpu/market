-- Schreibt einen Bereich (von-bis Datum) aus einem flachen Array.
-- Aktualisiert first_date/last_date in ts_header.
CREATE OR REPLACE FUNCTION ts_write_15min_range(
    p_ts_id  BIGINT,
    p_from   DATE,
    p_to     DATE,       -- exklusiv
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

    UPDATE ts_header SET
        first_date = LEAST(first_date, p_from),
        last_date  = GREATEST(last_date, p_to - 1)
    WHERE ts_id = p_ts_id;

    RETURN v_days;
END;
$$ LANGUAGE plpgsql;
