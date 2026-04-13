-- Audit log of every prompt actually sent to the model
-- Enables full debuggability and future prompt iteration analysis
CREATE TABLE llm_requests (
    id                   BIGSERIAL PRIMARY KEY,
    prompt_template_id   BIGINT REFERENCES llm_prompts (id),
    prompt_key           TEXT NOT NULL,
    model                TEXT NOT NULL,
    rendered_prompt      TEXT NOT NULL,    -- fully rendered string sent to the model
    input_variables      JSONB,            -- variable values used for this render
    response_text        TEXT,
    plant_id             BIGINT REFERENCES plants (id),
    analysis_id          BIGINT REFERENCES plant_analyses (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_requests_plant_id ON llm_requests (plant_id);
CREATE INDEX idx_llm_requests_analysis_id ON llm_requests (analysis_id);
CREATE INDEX idx_llm_requests_prompt_key ON llm_requests (prompt_key);
