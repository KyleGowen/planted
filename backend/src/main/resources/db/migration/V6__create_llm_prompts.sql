-- Reusable versioned prompt templates (per Database-Prompt-Storage spec)
-- content is TEXT — never VARCHAR; prompt length is unpredictable
CREATE TABLE llm_prompts (
    id          BIGSERIAL PRIMARY KEY,
    prompt_key  TEXT NOT NULL,
    version     INTEGER NOT NULL DEFAULT 1,
    role        TEXT NOT NULL,          -- 'system', 'user', 'developer'
    content     TEXT NOT NULL,          -- plain TEXT body, never JSONB
    format      TEXT NOT NULL DEFAULT 'text', -- 'text', 'markdown', 'json', 'handlebars'
    variables   JSONB,                  -- placeholder metadata e.g. {"required": ["plant_name"]}
    tags        TEXT[] DEFAULT '{}',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX llm_prompts_key_version_role_uq ON llm_prompts (prompt_key, version, role);
CREATE INDEX idx_llm_prompts_key_active ON llm_prompts (prompt_key, is_active);

-- Seed initial prompt templates
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_registration_analysis_v1', 1, 'system',
    'You are a conservative plant identification and care expert. Analyze the provided plant image and return a structured JSON response. Be conservative: state uncertainty when the image is ambiguous or low quality. Never hallucinate species or variety. Provide practical, beginner-friendly care guidance. If the plant appears unhealthy, diagnose visible problems without assuming a single cause. Respect user goals when provided.',
    'text',
    '{"required": [], "optional": ["goals_text", "location"]}',
    ARRAY['registration', 'analysis']
),
(
    'plant_registration_analysis_v1', 1, 'user',
    'Please analyze this plant image. {{#if goals_text}}My goals for this plant: {{goals_text}}{{/if}} {{#if location}}Plant location: {{location}}{{/if}} Return a complete JSON analysis matching the required schema.',
    'handlebars',
    '{"required": [], "optional": ["goals_text", "location"]}',
    ARRAY['registration', 'analysis']
),
(
    'plant_info_panel_v1', 1, 'system',
    'You are a knowledgeable botanist. Provide engaging, accurate information about the given plant species. Focus on: native regions and natural habitat, interesting historical or cultural context, fascinating biological facts, and practical or culinary uses if clearly appropriate and safe. Keep responses informative but accessible.',
    'text',
    '{"required": ["genus", "species"]}',
    ARRAY['info', 'panel']
),
(
    'plant_info_panel_v1', 1, 'user',
    'Provide an informative species profile for {{genus}} {{species}}{{#if variety}} ({{variety}}){{/if}}. Return structured JSON with native_regions, history, interesting_facts (array), and uses (array, only if clearly safe and relevant).',
    'handlebars',
    '{"required": ["genus", "species"], "optional": ["variety"]}',
    ARRAY['info', 'panel']
),
(
    'plant_reminder_recompute_v1', 1, 'system',
    'You are a plant care scheduling assistant. Based on the plant species care requirements and recent care history, determine whether watering, fertilizing, or pruning is due. Consider time of year and any provided weather context. Return specific, actionable next-step instructions. Be practical and concise.',
    'text',
    '{"required": ["species", "watering_frequency", "last_watered_at"]}',
    ARRAY['reminder', 'recompute']
),
(
    'plant_reminder_recompute_v1', 1, 'user',
    'Plant: {{genus}} {{species}}. Watering frequency: {{watering_frequency}}. Last watered: {{last_watered_at}}. Last fertilized: {{last_fertilized_at}}. Last pruned: {{last_pruned_at}}. Current date: {{current_date}}. {{#if weather_context}}Weather: {{weather_context}}{{/if}} Determine if watering, fertilizing, or pruning is due and provide next-step instructions.',
    'handlebars',
    '{"required": ["genus", "species", "watering_frequency", "last_watered_at", "current_date"], "optional": ["last_fertilized_at", "last_pruned_at", "weather_context"]}',
    ARRAY['reminder', 'recompute']
),
(
    'pruning_analysis_v1', 1, 'system',
    'You are a conservative plant pruning advisor. Review the provided plant images and give pruning guidance. Important: "No pruning required" is a valid and preferred answer when the plant does not clearly need pruning. Only recommend pruning when there is clear evidence of dead, damaged, crowded, or goal-impeding growth. Avoid aggressive pruning recommendations. Tie suggestions to the user''s stated goals when possible.',
    'text',
    '{"required": ["genus", "species"]}',
    ARRAY['pruning', 'analysis']
),
(
    'pruning_analysis_v1', 1, 'user',
    'Plant: {{genus}} {{species}}. {{#if goals_text}}User goals: {{goals_text}}{{/if}} {{#if pruning_guidance}}Species pruning guidance: {{pruning_guidance}}{{/if}} Please review the attached images ({{image_count}} photo(s) from different angles) and provide conservative pruning recommendations. Return structured JSON.',
    'handlebars',
    '{"required": ["genus", "species", "image_count"], "optional": ["goals_text", "pruning_guidance"]}',
    ARRAY['pruning', 'analysis']
);
