-- Version 2 of registration and pruning prompts — adds plant name, prior care context,
-- and care history variables so OpenAI has full context on reanalysis and pruning.

-- Deactivate v1 registration prompts
UPDATE llm_prompts SET is_active = FALSE
WHERE prompt_key = 'plant_registration_analysis_v1' AND version = 1;

INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_registration_analysis_v1', 2, 'system',
    'You are a conservative plant identification and care expert. Analyze the provided plant image and return a structured JSON response. Be conservative: state uncertainty when the image is ambiguous or low quality. Never hallucinate species or variety. Provide practical, beginner-friendly care guidance. If the plant appears unhealthy, diagnose visible problems without assuming a single cause. Respect user goals when provided. When prior care context or care history is provided, use that information to give continuity in care recommendations and refine your analysis accordingly.',
    'text',
    '{"required": [], "optional": ["plant_name", "goals_text", "location", "prior_care_context", "care_history"]}',
    ARRAY['registration', 'analysis']
),
(
    'plant_registration_analysis_v1', 2, 'user',
    E'Please analyze this plant image.{{#if plant_name}}\nPlant name: {{plant_name}}.{{/if}}{{#if goals_text}}\nMy goals for this plant: {{goals_text}}{{/if}}{{#if location}}\nPlant location: {{location}}{{/if}}{{#if prior_care_context}}\nPrior care context from previous analysis:\n{{prior_care_context}}{{/if}}{{#if care_history}}\nRecent care history: {{care_history}}{{/if}}\nReturn a complete JSON analysis matching the required schema.',
    'handlebars',
    '{"required": [], "optional": ["plant_name", "goals_text", "location", "prior_care_context", "care_history"]}',
    ARRAY['registration', 'analysis']
)
ON CONFLICT (prompt_key, version, role) DO NOTHING;

-- Deactivate v1 pruning prompts
UPDATE llm_prompts SET is_active = FALSE
WHERE prompt_key = 'pruning_analysis_v1' AND version = 1;

INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'pruning_analysis_v1', 2, 'system',
    'You are a conservative plant pruning advisor. Review the provided plant images and give pruning guidance. Important: "No pruning required" is a valid and preferred answer when the plant does not clearly need pruning. Only recommend pruning when there is clear evidence of dead, damaged, crowded, or goal-impeding growth. Avoid aggressive pruning recommendations. Tie suggestions to the user''s stated goals when possible. When care history is provided, consider recent care events in your pruning assessment.',
    'text',
    '{"required": ["genus", "species", "image_count"], "optional": ["goals_text", "pruning_guidance", "care_history"]}',
    ARRAY['pruning', 'analysis']
),
(
    'pruning_analysis_v1', 2, 'user',
    E'Plant: {{genus}} {{species}}.{{#if goals_text}}\nUser goals: {{goals_text}}{{/if}}{{#if pruning_guidance}}\nSpecies pruning guidance: {{pruning_guidance}}{{/if}}{{#if care_history}}\nRecent care history: {{care_history}}{{/if}}\nPlease review the attached images ({{image_count}} photo(s) from different angles) and provide conservative pruning recommendations. Return structured JSON.',
    'handlebars',
    '{"required": ["genus", "species", "image_count"], "optional": ["goals_text", "pruning_guidance", "care_history"]}',
    ARRAY['pruning', 'analysis']
)
ON CONFLICT (prompt_key, version, role) DO NOTHING;
