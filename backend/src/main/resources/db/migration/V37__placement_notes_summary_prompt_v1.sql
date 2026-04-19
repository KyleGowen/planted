-- Dedicated prompt for compressing an owner's placement notes (plants.location) into a
-- single short caption shown as the third line under the Indoor/Outdoor label. Runs as
-- its own tiny text-only LLM call (plant_placement_notes_summary_v1), independent of
-- the registration/reanalysis pipeline.

INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags)
VALUES (
    'plant_placement_notes_summary_v1', 1, 'system',
    $ps1sys$You rewrite an owner's free-text notes about where their plant lives into a single short caption for a mobile UI.

Rules for the "summary" output:
- Exactly one line, one sentence. No line breaks, no bullet points, no list markers, no quotation marks.
- Aim for well under 100 characters; never exceed 120.
- Paraphrase what the owner wrote — do NOT copy it verbatim. Keep the most useful placement fact (room, window direction, light, indoor/outdoor, balcony, shelf, etc.) and drop filler. Leaving information out is fine and usually expected.
- Correct obvious typos. Use neutral, descriptive phrasing; title-ish sentence case is fine, e.g. "Living room east window with morning sun" or "West-facing balcony, afternoon shade".
- Do NOT add any advice, recommendations, species context, climate, geography, owner name, or evaluative language ("great spot", "too dark", "perfect light").
- Do NOT invent any detail that is not present or directly implied by the owner's notes (no made-up window direction, floor level, light duration, or building material).
- If the owner's notes are missing, blank, or contain no usable placement information, return an empty string for "summary" — do not fabricate a location.$ps1sys$,
    'text',
    '{"required": [], "optional": ["location"]}',
    ARRAY['placement', 'summary']
),
(
    'plant_placement_notes_summary_v1', 1, 'user',
    $ps1usr$Owner's placement notes:
{{location}}

Return JSON matching the schema. Put your single-sentence paraphrase in "summary". If the notes are blank or unusable, return an empty string.$ps1usr$,
    'handlebars',
    '{"required": ["location"], "optional": []}',
    ARRAY['placement', 'summary']
)
ON CONFLICT (prompt_key, version, role) DO UPDATE
    SET content     = EXCLUDED.content,
        format      = EXCLUDED.format,
        variables   = EXCLUDED.variables,
        tags        = EXCLUDED.tags,
        is_active   = TRUE,
        updated_at  = NOW();
