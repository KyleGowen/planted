-- Geographic location fields on plants for climate-aware watering recommendations
ALTER TABLE plants
    ADD COLUMN geo_country TEXT,
    ADD COLUMN geo_state   TEXT,
    ADD COLUMN geo_city    TEXT;

-- Insert new version 2 prompts for plant registration analysis that include geographic location.
-- Geographic context allows the LLM to factor in regional climate, humidity, and seasonal patterns
-- when generating watering frequency and care guidance.
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags)
VALUES
(
    'plant_registration_analysis_v1', 2, 'system',
    'You are a conservative plant identification and care expert. Analyze the provided plant image and return a structured JSON response. Be conservative: state uncertainty when the image is ambiguous or low quality. Never hallucinate species or variety. Provide practical, beginner-friendly care guidance tailored to the owner''s environment. When geographic location is provided, factor in the local climate, typical seasonal weather patterns, humidity, and temperature ranges for that region when recommending watering frequency and care schedules — for example, plants in hot, dry climates need more frequent watering than the same species in cool, humid climates. If the plant appears unhealthy, diagnose visible problems without assuming a single cause. Respect user goals when provided.',
    'text',
    '{"required": [], "optional": ["goals_text", "location", "geographic_location"]}',
    ARRAY['registration', 'analysis']
),
(
    'plant_registration_analysis_v1', 2, 'user',
    'Please analyze this plant image. {{#if goals_text}}My goals for this plant: {{goals_text}}{{/if}} {{#if location}}Indoor placement: {{location}}{{/if}} {{#if geographic_location}}Geographic location: {{geographic_location}}. Please factor in the local climate, typical seasonal weather, and humidity of this region when recommending watering frequency and care guidance.{{/if}} Return a complete JSON analysis matching the required schema.',
    'handlebars',
    '{"required": [], "optional": ["goals_text", "location", "geographic_location"]}',
    ARRAY['registration', 'analysis']
);

-- Deactivate the old version 1 prompts
UPDATE llm_prompts
SET is_active = FALSE
WHERE prompt_key = 'plant_registration_analysis_v1'
  AND version = 1;
