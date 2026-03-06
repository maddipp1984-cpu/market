-- Auth-System: Berechtigungen auf 3 Ebenen (Seite, Objekttyp, Feld)

CREATE TABLE ts_auth_user (
    user_id       UUID PRIMARY KEY,
    username      VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    is_admin      BOOLEAN DEFAULT false,
    created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE ts_auth_group (
    group_id      SERIAL PRIMARY KEY,
    name          VARCHAR(100) UNIQUE NOT NULL,
    description   TEXT
);

CREATE TABLE ts_auth_group_member (
    group_id      INT REFERENCES ts_auth_group ON DELETE CASCADE,
    user_id       UUID REFERENCES ts_auth_user ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE ts_auth_resource (
    resource_key    VARCHAR(100) PRIMARY KEY,
    label           VARCHAR(255) NOT NULL,
    has_type_scope  BOOLEAN DEFAULT false
);

INSERT INTO ts_auth_resource VALUES
    ('dashboard',          'Dashboard',           false),
    ('objekte',            'Objekte',              true),
    ('zeitreihen',         'Zeitreihen',           true),
    ('einheiten',          'Einheiten',            false),
    ('waehrungen',         'Waehrungen',           false),
    ('objekttypen',        'Objekttypen',          false),
    ('einstellungen',      'Einstellungen',        false);

CREATE TABLE ts_auth_permission (
    permission_id   SERIAL PRIMARY KEY,
    group_id        INT NOT NULL REFERENCES ts_auth_group ON DELETE CASCADE,
    resource_key    VARCHAR(100) NOT NULL REFERENCES ts_auth_resource,
    object_type_id  SMALLINT,
    can_read        BOOLEAN DEFAULT false,
    can_write       BOOLEAN DEFAULT false,
    can_delete      BOOLEAN DEFAULT false
);
CREATE UNIQUE INDEX uq_auth_permission ON ts_auth_permission (group_id, resource_key, COALESCE(object_type_id, 0));

CREATE TABLE ts_auth_field_restriction (
    restriction_id  SERIAL PRIMARY KEY,
    group_id        INT NOT NULL REFERENCES ts_auth_group ON DELETE CASCADE,
    resource_key    VARCHAR(100) NOT NULL REFERENCES ts_auth_resource,
    field_key       VARCHAR(100) NOT NULL,
    object_type_id  SMALLINT
);
CREATE UNIQUE INDEX uq_auth_field_restriction ON ts_auth_field_restriction (group_id, resource_key, field_key, COALESCE(object_type_id, 0));
