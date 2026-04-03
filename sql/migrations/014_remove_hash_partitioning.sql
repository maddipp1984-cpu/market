-- Entfernt Hash-Partitionierung von ts_values_day, ts_values_1h, ts_values_15min.
-- Single-Node: Hash erzeugt nur unnoetige Chunks ohne Performance-Vorteil.
-- ACHTUNG: Keine Transaktion — jede Tabelle einzeln (Dekompression braucht exklusive Locks).
-- Reihenfolge: day (klein) -> 1h -> 15min (gross).

-- ============================================================
-- 1. ts_values_day: Hash(ts_id, 4) entfernen
-- ============================================================

DO $$ DECLARE chunk_fqn TEXT; BEGIN
  FOR chunk_fqn IN SELECT format('%I.%I', c.chunk_schema, c.chunk_name)
    FROM timescaledb_information.chunks c
    WHERE c.hypertable_name = 'ts_values_day' AND c.is_compressed = true
  LOOP EXECUTE format('SELECT decompress_chunk(%L)', chunk_fqn); END LOOP;
END $$;
SELECT remove_compression_policy('ts_values_day', if_exists => true);

CREATE TABLE ts_values_day_new (
    ts_id   BIGINT           NOT NULL,
    ts_date DATE             NOT NULL,
    value   DOUBLE PRECISION
);
SELECT create_hypertable('ts_values_day_new', by_range('ts_date', INTERVAL '1 year'));

INSERT INTO ts_values_day_new SELECT * FROM ts_values_day;

DROP TABLE ts_values_day;
ALTER TABLE ts_values_day_new RENAME TO ts_values_day;

CREATE UNIQUE INDEX idx_day_pk ON ts_values_day (ts_id, ts_date);
ALTER TABLE ts_values_day SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_day', INTERVAL '2 years');
