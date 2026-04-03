-- ts_values_15min: Hash(ts_id, 8) entfernen

DO $$ DECLARE chunk_fqn TEXT; BEGIN
  FOR chunk_fqn IN SELECT format('%I.%I', c.chunk_schema, c.chunk_name)
    FROM timescaledb_information.chunks c
    WHERE c.hypertable_name = 'ts_values_15min' AND c.is_compressed = true
  LOOP EXECUTE format('SELECT decompress_chunk(%L)', chunk_fqn); END LOOP;
END $$;
SELECT remove_compression_policy('ts_values_15min', if_exists => true);

CREATE TABLE ts_values_15min_new (
    ts_id   BIGINT             NOT NULL,
    ts_date DATE               NOT NULL,
    vals    DOUBLE PRECISION[] NOT NULL
);
SELECT create_hypertable('ts_values_15min_new', by_range('ts_date', INTERVAL '1 year'));

INSERT INTO ts_values_15min_new SELECT * FROM ts_values_15min;

DROP TABLE ts_values_15min;
ALTER TABLE ts_values_15min_new RENAME TO ts_values_15min;

CREATE UNIQUE INDEX idx_15min_pk ON ts_values_15min (ts_id, ts_date);
ALTER TABLE ts_values_15min SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_15min', INTERVAL '3 months');
