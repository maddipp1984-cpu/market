CREATE OR REPLACE FUNCTION ts_sum_1h(
    p_ts_ids BIGINT[],
    p_start  DATE,
    p_end    DATE
)
RETURNS TABLE(ts_date DATE, vals DOUBLE PRECISION[])
LANGUAGE sql STABLE
AS $$
    SELECT
        t.ts_date,
        ARRAY(
            SELECT COALESCE(SUM(elem), 0)
            FROM ts_values_1h v,
                 LATERAL unnest(v.vals) WITH ORDINALITY AS u(elem, idx)
            WHERE v.ts_date = t.ts_date
              AND v.ts_id = ANY(p_ts_ids)
            GROUP BY idx
            ORDER BY idx
        ) AS vals
    FROM (
        SELECT DISTINCT ts_date
        FROM ts_values_1h
        WHERE ts_id = ANY(p_ts_ids)
          AND ts_date >= p_start
          AND ts_date < p_end
    ) t
    ORDER BY t.ts_date;
$$;
