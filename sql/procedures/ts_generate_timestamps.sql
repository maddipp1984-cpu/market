-- Erzeugt alle Timestamps für einen Tag (DST-korrekt)
CREATE OR REPLACE FUNCTION ts_generate_timestamps(
    p_date     DATE,
    p_timezone TEXT,
    p_interval INTERVAL
) RETURNS TIMESTAMPTZ[] AS $$
    SELECT ARRAY(
        SELECT generate_series(
            p_date::TIMESTAMP AT TIME ZONE p_timezone,
            (p_date + 1)::TIMESTAMP AT TIME ZONE p_timezone - p_interval,
            p_interval
        )
    );
$$ LANGUAGE sql IMMUTABLE STRICT;
