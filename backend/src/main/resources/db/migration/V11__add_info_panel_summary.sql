-- Narrative history summary for INFO_PANEL analyses (async LLM job)
ALTER TABLE plant_analyses
    ADD COLUMN IF NOT EXISTS info_panel_summary TEXT;

-- Prompts: owner journal + care timeline → single narrative summary
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags)
VALUES (
    'plant_history_summary_v1', 1, 'system',
    'You are a careful assistant helping summarize a single houseplant''s documented history for its owner.
Rules:
- Write a clear, chronological or narrative summary based ONLY on the structured timeline and image context provided.
- Do not invent events, dates, symptoms, or care actions that are not supported by the input.
- When images are included, treat them as supporting context only; if you cannot see them clearly, say so briefly rather than guessing.
- Use conservative language when inferring plant condition from photos.
- Keep the tone warm and practical; 2–5 short paragraphs unless the timeline is very sparse.',
    'text',
    '{"required": [], "optional": ["plant_name", "species_label", "timeline_text", "image_count"]}',
    ARRAY['history', 'summary', 'info_panel']
)
ON CONFLICT (prompt_key, version, role) DO NOTHING;

INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags)
VALUES (
    'plant_history_summary_v1', 1, 'user',
    '{{#if plant_name}}Plant nickname: {{plant_name}}
{{/if}}{{#if species_label}}Species (if known): {{species_label}}
{{/if}}
Below is the owner''s journal and care-event log. Summarize this plant''s history for the About page.
{{#if image_count}}There are {{image_count}} journal photo(s) attached after this text; reference them only when relevant.
{{/if}}

{{timeline_text}}',
    'handlebars',
    '{"required": ["timeline_text"], "optional": ["plant_name", "species_label", "image_count"]}',
    ARRAY['history', 'summary', 'info_panel']
)
ON CONFLICT (prompt_key, version, role) DO NOTHING;
