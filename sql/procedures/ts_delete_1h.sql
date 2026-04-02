-- Loescht 1h-Werte fuer eine Zeitreihe (optional eingeschraenkt auf Datumsbereich).
-- Berechnet first_date/last_date in ts_header neu.
CREATE OR REPLACE FUNCTION ts_delete_1h(
    p_ts_id  BIGINT,
    p_from   DATE DEFAULT NULL,
    p_to     DATE DEFAULT NULL
) RETURNS INTEGER AS $$
DECLARE
    v_count      INTEGER;
    v_first_date DATE;
    v_last_date  DATE;
BEGIN
    DELETE FROM ts_values_1h
    WHERE ts_id = p_ts_id
      AND (p_from IS NULL OR ts_date >= p_from)
      AND (p_to IS NULL OR ts_date < p_to);

    GET DIAGNOSTICS v_count = ROW_COUNT;

    SELECT MIN(ts_date), MAX(ts_date)
    INTO v_first_date, v_last_date
    FROM ts_values_1h
    WHERE ts_id = p_ts_id;

    UPDATE ts_header SET
        first_date = v_first_date,
        last_date  = v_last_date
    WHERE ts_id = p_ts_id;

    RETURN v_count;
END;
$$ LANGUAGE plpgsql;
