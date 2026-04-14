-- Richer plant history summary: profile + goals + care snapshot, sectioned output, new prompt key v2

UPDATE llm_prompts
SET is_active = FALSE
WHERE prompt_key = 'plant_history_summary_v1';

INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags)
VALUES (
    'plant_history_summary_v2', 1, 'system',
    'You are a careful assistant summarizing a single houseplant''s documented history for its owner''s About page.
Rules:
- Use ONLY the plant profile, timeline, care snapshot, and attached images. Do not invent events, dates, symptoms, or care actions.
- When images are included, treat them as supporting context; if unclear, say so briefly instead of guessing. Use conservative language for plant condition from photos.
- Relate owner-stated goals and any goal suggestions from the care snapshot to what the timeline shows. If evidence is thin, say what is unknown.
- Organize the summary with clear === Section === headings (same style as the timeline). Suggested sections: === Overview ===, === Care and observations ===, === Goals and progress ===, === Photos === (only if images helped). Skip empty sections.
- Keep the tone warm and practical; default length is moderate unless the timeline is very sparse.',
    'text',
    '{"required": ["timeline_text"], "optional": ["plant_profile", "baseline_photo_note", "plant_name", "species_label", "image_count"]}',
    ARRAY['history', 'summary', 'info_panel']
)
ON CONFLICT (prompt_key, version, role) DO NOTHING;

INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags)
VALUES (
    'plant_history_summary_v2', 1, 'user',
    E'{{#if plant_name}}Plant nickname: {{plant_name}}
{{/if}}{{#if species_label}}Species (if known): {{species_label}}
{{/if}}{{#if plant_profile}}=== Plant profile and care snapshot ===
{{plant_profile}}

{{/if}}{{#if baseline_photo_note}}Note: {{baseline_photo_note}}

{{/if}}Below is the owner''s journal and care-event log. Summarize this plant''s history and goal progress for the About page.
{{#if image_count}}Attached images: {{image_count}}.
{{/if}}

{{timeline_text}}',
    'handlebars',
    '{"required": ["timeline_text"], "optional": ["plant_profile", "baseline_photo_note", "plant_name", "species_label", "image_count"]}',
    ARRAY['history', 'summary', 'info_panel']
)
ON CONFLICT (prompt_key, version, role) DO NOTHING;
