-- Index-Tabelle (Preisindices, Temperaturkurven etc.)
CREATE TABLE ts_index (
    index_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_id   BIGINT NOT NULL UNIQUE REFERENCES ts_object(object_id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
