-- Liest eine 15min-Zeitreihe und expandiert Arrays zu (timestamp, value) Paaren
CREATE OR REPLACE FUNCTION ts_read_15min(
    p_ts_id  BIGINT,
    p_from   DATE DEFAULT NULL,
    p_to     DATE DEFAULT NULL    -- exklusiv
) RETURNS TABLE(ts_time TIMESTAMPTZ, value DOUBLE PRECISION) AS $$
DECLARE
    v_timezone TEXT;
BEGIN
    SELECT timezone INTO STRICT v_timezone
    FROM ts_header WHERE ts_id = p_ts_id;

    RETURN QUERY
    SELECT
        (d.ts_date::TIMESTAMP AT TIME ZONE v_timezone) + ((gs.i - 1) * INTERVAL '15 minutes'),
        d.vals[gs.i]
    FROM ts_values_15min d
    CROSS JOIN LATERAL generate_series(1, array_length(d.vals, 1)) AS gs(i)
    WHERE d.ts_id = p_ts_id
      AND (p_from IS NULL OR d.ts_date >= p_from)
      AND (p_to IS NULL OR d.ts_date < p_to)
    ORDER BY d.ts_date, gs.i;
END;
$$ LANGUAGE plpgsql STABLE;
