-- Per-section cache for decomposed plant bio content. Replaces the monolithic
-- registration analysis with nine independently refreshable sections (2 vision
-- + 7 text). Each row is the cached output of a single small LLM call for one
-- section of one plant; reads are served straight from this table and refreshes
-- are enqueued lazily when stale.

CREATE TABLE plant_bio_sections (
    id                   BIGSERIAL PRIMARY KEY,
    plant_id             BIGINT NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    section_key          VARCHAR(64) NOT NULL,
    status               VARCHAR(24) NOT NULL,
    content_jsonb        JSONB,
    prompt_key           VARCHAR(128),
    prompt_version       INT,
    inputs_fingerprint   VARCHAR(64),
    generated_at         TIMESTAMPTZ,
    last_error           TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT plant_bio_sections_unique UNIQUE (plant_id, section_key)
);

CREATE INDEX idx_plant_bio_sections_stale ON plant_bio_sections (generated_at);
CREATE INDEX idx_plant_bio_sections_plant ON plant_bio_sections (plant_id);
