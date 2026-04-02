-- 011_series_type.sql
-- Neue Tabelle: Reihenarten (Series Types)

CREATE TABLE ts_series_type (
    series_type_id  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    category        SMALLINT NOT NULL CHECK (category IN (1, 2))
);

COMMENT ON TABLE ts_series_type IS 'Reihenarten: Klassifikation von Zeitreihen';
COMMENT ON COLUMN ts_series_type.category IS '1=Finanziell, 2=Physikalisch';

-- FK in ts_header (nullable zunaechst, da bestehende Daten keinen Wert haben)
ALTER TABLE ts_header ADD COLUMN series_type_id SMALLINT REFERENCES ts_series_type(series_type_id);
CREATE INDEX idx_header_series_type ON ts_header (series_type_id);
