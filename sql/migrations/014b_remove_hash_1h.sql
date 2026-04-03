-- ts_values_1h: Hash(ts_id, 4) entfernen

DO $$ DECLARE chunk_fqn TEXT; BEGIN
  FOR chunk_fqn IN SELECT format('%I.%I', c.chunk_schema, c.chunk_name)
    FROM timescaledb_information.chunks c
    WHERE c.hypertable_name = 'ts_values_1h' AND c.is_compressed = true
  LOOP EXECUTE format('SELECT decompress_chunk(%L)', chunk_fqn); END LOOP;
END $$;
SELECT remove_compression_policy('ts_values_1h', if_exists => true);

CREATE TABLE ts_values_1h_new (
    ts_id   BIGINT             NOT NULL,
    ts_date DATE               NOT NULL,
    vals    DOUBLE PRECISION[] NOT NULL
);
SELECT create_hypertable('ts_values_1h_new', by_range('ts_date', INTERVAL '1 year'));

INSERT INTO ts_values_1h_new SELECT * FROM ts_values_1h;

DROP TABLE ts_values_1h;
ALTER TABLE ts_values_1h_new RENAME TO ts_values_1h;

CREATE UNIQUE INDEX idx_1h_pk ON ts_values_1h (ts_id, ts_date);
ALTER TABLE ts_values_1h SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ts_id',
    timescaledb.compress_orderby = 'ts_date'
);
SELECT add_compression_policy('ts_values_1h', INTERVAL '6 months');
