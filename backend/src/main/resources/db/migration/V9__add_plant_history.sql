-- Plant history entries: timestamped text notes and/or images attached to a plant
CREATE TABLE IF NOT EXISTS plant_history_entries (
    id          BIGSERIAL PRIMARY KEY,
    plant_id    BIGINT NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    note_text   TEXT,
    image_id    BIGINT REFERENCES plant_images (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT plant_history_note_length CHECK (note_text IS NULL OR LENGTH(note_text) <= 180),
    CONSTRAINT plant_history_has_content CHECK (note_text IS NOT NULL OR image_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_plant_history_plant_id ON plant_history_entries (plant_id, created_at DESC);

-- Expand plant_images image_type check to include HISTORY_NOTE (idempotent)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'plant_images_image_type_check' AND contype = 'c'
    ) THEN
        ALTER TABLE plant_images DROP CONSTRAINT plant_images_image_type_check;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'plant_images_image_type_check' AND contype = 'c'
    ) THEN
        ALTER TABLE plant_images ADD CONSTRAINT plant_images_image_type_check CHECK (
            image_type IN ('ORIGINAL_UPLOAD', 'HEALTHY_REFERENCE', 'ILLUSTRATED', 'PRUNE_UPDATE', 'HISTORY_NOTE')
        );
    END IF;
END$$;

-- Deactivate v1 user prompts that don't include care_history or history_notes
UPDATE llm_prompts
SET is_active = FALSE
WHERE prompt_key IN ('plant_registration_analysis_v1', 'pruning_analysis_v1')
  AND role = 'user'
  AND version = 1;

-- v2 user prompt for plant registration / reanalysis
-- Updates existing v2 user prompt (if already applied) to include history_notes support
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags)
VALUES (
    'plant_registration_analysis_v1', 2, 'user',
    'Please analyze this plant image.
{{#if plant_name}}This plant has been named: {{plant_name}}{{/if}}
{{#if goals_text}}My goals for this plant: {{goals_text}}{{/if}}
{{#if location}}Plant location: {{location}}{{/if}}
{{#if prior_care_context}}Previous care profile for this plant:
{{prior_care_context}}{{/if}}
{{#if care_history}}Recent care events: {{care_history}}{{/if}}
{{#if history_notes}}Owner observations and notes:
{{history_notes}}{{/if}}
Return a complete JSON analysis matching the required schema.',
    'handlebars',
    '{"required": [], "optional": ["plant_name", "goals_text", "location", "prior_care_context", "care_history", "history_notes"]}',
    ARRAY['registration', 'analysis']
)
ON CONFLICT (prompt_key, version, role) DO UPDATE
    SET content   = EXCLUDED.content,
        variables = EXCLUDED.variables;

-- v2 user prompt for pruning analysis
-- Updates existing v2 user prompt (if already applied) to include history_notes support
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags)
VALUES (
    'pruning_analysis_v1', 2, 'user',
    'Plant: {{genus}} {{species}}.
{{#if goals_text}}User goals: {{goals_text}}{{/if}}
{{#if pruning_guidance}}Species pruning guidance: {{pruning_guidance}}{{/if}}
{{#if care_history}}Recent care events: {{care_history}}{{/if}}
{{#if history_notes}}Owner observations and notes:
{{history_notes}}{{/if}}
Please review the attached images ({{image_count}} photo(s) from different angles) and provide conservative pruning recommendations. Return structured JSON.',
    'handlebars',
    '{"required": ["genus", "species", "image_count"], "optional": ["goals_text", "pruning_guidance", "care_history", "history_notes"]}',
    ARRAY['pruning', 'analysis']
)
ON CONFLICT (prompt_key, version, role) DO UPDATE
    SET content   = EXCLUDED.content,
        variables = EXCLUDED.variables;
