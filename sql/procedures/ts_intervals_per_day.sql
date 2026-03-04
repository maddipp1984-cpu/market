-- Berechnet die Anzahl Intervalle für einen Tag unter Berücksichtigung von DST
-- Beispiel: ts_intervals_per_day('2025-10-26', '15 minutes') = 100
CREATE OR REPLACE FUNCTION ts_intervals_per_day(
    p_date     DATE,
    p_interval INTERVAL
) RETURNS INTEGER AS $$
    SELECT (EXTRACT(EPOCH FROM (
        (p_date + 1)::TIMESTAMP AT TIME ZONE 'Europe/Berlin'
        - p_date::TIMESTAMP AT TIME ZONE 'Europe/Berlin'
    )) / EXTRACT(EPOCH FROM p_interval))::INTEGER;
$$ LANGUAGE sql IMMUTABLE STRICT;
