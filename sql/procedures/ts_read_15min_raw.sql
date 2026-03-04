-- Liest 15min-Rohdaten (ohne Expansion) -- schneller wenn Java die Timestamps berechnet
CREATE OR REPLACE FUNCTION ts_read_15min_raw(
    p_ts_id  BIGINT,
    p_from   DATE DEFAULT NULL,
    p_to     DATE DEFAULT NULL
) RETURNS TABLE(ts_date DATE, vals DOUBLE PRECISION[]) AS $$
BEGIN
    RETURN QUERY
    SELECT d.ts_date, d.vals
    FROM ts_values_15min d
    WHERE d.ts_id = p_ts_id
      AND (p_from IS NULL OR d.ts_date >= p_from)
      AND (p_to IS NULL OR d.ts_date < p_to)
    ORDER BY d.ts_date;
END;
$$ LANGUAGE plpgsql STABLE;
