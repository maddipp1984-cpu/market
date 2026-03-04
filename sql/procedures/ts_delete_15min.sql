-- Löscht 15min-Werte für eine Zeitreihe (optional eingeschränkt auf Datumsbereich)
CREATE OR REPLACE FUNCTION ts_delete_15min(
    p_ts_id  BIGINT,
    p_from   DATE DEFAULT NULL,
    p_to     DATE DEFAULT NULL
) RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    DELETE FROM ts_values_15min
    WHERE ts_id = p_ts_id
      AND (p_from IS NULL OR ts_date >= p_from)
      AND (p_to IS NULL OR ts_date < p_to);

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;
