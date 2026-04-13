CREATE TABLE plants (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     TEXT,
    name                        TEXT,
    location                    TEXT,
    goals_text                  TEXT,
    status                      TEXT NOT NULL DEFAULT 'ACTIVE',
    primary_image_id            BIGINT,
    illustrated_image_asset_id  BIGINT,
    species_label               TEXT,
    genus                       TEXT,
    species                     TEXT,
    variety                     TEXT,
    class_name                  TEXT,
    health_attention_needed     BOOLEAN NOT NULL DEFAULT FALSE,
    goal_attention_needed       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at                 TIMESTAMPTZ,

    CONSTRAINT plants_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_plants_status ON plants (status);
CREATE INDEX idx_plants_user_id ON plants (user_id);
