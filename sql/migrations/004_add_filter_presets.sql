CREATE TABLE ts_filter_preset (
    preset_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    page_key     VARCHAR(50) NOT NULL,
    user_id      VARCHAR(100),
    name         VARCHAR(255) NOT NULL,
    conditions   JSONB NOT NULL,
    scope        VARCHAR(10) NOT NULL DEFAULT 'USER',
    is_default   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_scope CHECK (scope IN ('GLOBAL', 'USER')),
    CONSTRAINT chk_user_scope CHECK (
        (scope = 'GLOBAL' AND user_id IS NULL) OR
        (scope = 'USER' AND user_id IS NOT NULL)
    )
);
CREATE INDEX idx_preset_page_user ON ts_filter_preset (page_key, user_id);
CREATE INDEX idx_preset_page_scope ON ts_filter_preset (page_key, scope);

CREATE UNIQUE INDEX idx_preset_default_global
    ON ts_filter_preset (page_key) WHERE is_default = TRUE AND scope = 'GLOBAL';
CREATE UNIQUE INDEX idx_preset_default_user
    ON ts_filter_preset (page_key, user_id) WHERE is_default = TRUE AND scope = 'USER';
