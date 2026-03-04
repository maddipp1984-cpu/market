-- Migration 003: Übergeordnete Objekte (ts_object)
-- Zeitreihen können einem Objekt zugeordnet werden (1:n)

-- Objekttypen (Referenztabelle)
CREATE TABLE ts_object_type (
    type_id      SMALLINT PRIMARY KEY,
    type_key     VARCHAR(50) NOT NULL UNIQUE,
    description  TEXT
);

INSERT INTO ts_object_type (type_id, type_key, description) VALUES
    (1, 'CONTRACT_VHP',    'Vertrag VHP'),
    (2, 'CONTRACT',        'Vertrag'),
    (3, 'CONTRACT_VERANS', 'Vertragsanschluss'),
    (4, 'ANS',             'Anschluss');

-- Objekte
CREATE TABLE ts_object (
    object_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type_id      SMALLINT NOT NULL,
    object_key   VARCHAR(255) NOT NULL UNIQUE,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_object_type FOREIGN KEY (type_id) REFERENCES ts_object_type (type_id)
);

CREATE INDEX idx_object_type ON ts_object (type_id);

-- ts_header um object_id erweitern
ALTER TABLE ts_header ADD COLUMN object_id BIGINT;
ALTER TABLE ts_header ADD CONSTRAINT fk_header_object FOREIGN KEY (object_id) REFERENCES ts_object (object_id);
CREATE INDEX idx_header_object ON ts_header (object_id);
