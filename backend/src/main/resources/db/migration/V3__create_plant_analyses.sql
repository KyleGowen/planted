CREATE TABLE plant_analyses (
    id                        BIGSERIAL PRIMARY KEY,
    plant_id                  BIGINT NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    analysis_type             TEXT NOT NULL,
    status                    TEXT NOT NULL DEFAULT 'PENDING',

    -- Taxonomy
    class_name                TEXT,
    genus                     TEXT,
    species                   TEXT,
    variety                   TEXT,
    scientific_name           TEXT,
    confidence                TEXT,

    -- Care — normalized fields
    native_regions_json       JSONB,
    placement_guidance        TEXT,
    light_needs               TEXT,
    watering_guidance         TEXT,
    watering_amount           TEXT,
    watering_frequency        TEXT,
    fertilizer_guidance       TEXT,
    fertilizer_type           TEXT,
    fertilizer_frequency      TEXT,
    pruning_guidance          TEXT,
    propagation_instructions  TEXT,
    health_diagnosis          TEXT,
    goal_suggestions          TEXT,
    interesting_facts_json    JSONB,
    uses_json                 JSONB,

    -- Raw LLM output preserved for future prompt iteration
    raw_model_response_jsonb  JSONB,

    failure_reason            TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at              TIMESTAMPTZ,

    CONSTRAINT plant_analyses_type_check CHECK (
        analysis_type IN ('REGISTRATION', 'REANALYSIS', 'PRUNING', 'REMINDER', 'INFO_PANEL')
    ),
    CONSTRAINT plant_analyses_status_check CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    )
);

CREATE INDEX idx_plant_analyses_plant_id ON plant_analyses (plant_id);
CREATE INDEX idx_plant_analyses_status ON plant_analyses (status);
CREATE INDEX idx_plant_analyses_plant_type ON plant_analyses (plant_id, analysis_type);
