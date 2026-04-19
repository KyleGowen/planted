-- Nine dedicated prompts that together replace the monolithic
-- plant_registration_analysis_v1 call. Each produces one narrow section cached
-- in plant_bio_sections. SPECIES_ID and HEALTH_ASSESSMENT take an image; the
-- seven care/description prompts are text-only and accept a resolved species
-- name as input so they can be regenerated cheaply whenever a single field
-- (placement, growing context, journal, etc.) changes.

--
-- Section 1: SPECIES_ID (vision)
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_species_id_v1', 1, 'system',
    $ps1sys$You are a conservative plant identification expert. Look at the plant image and return a structured JSON identification only. Never hallucinate a species; when unsure, leave fields blank and explain the uncertainty in "confidence". State the specific epithet (second word of the binomial) in "species", not the full binomial.$ps1sys$,
    'text',
    '{"required": [], "optional": ["plant_name", "location"]}',
    ARRAY['bio_section', 'species_id']
),
(
    'plant_species_id_v1', 1, 'user',
    $ps1usr$Identify the plant in the attached image.
{{#if plant_name}}Owner's nickname for this plant (weak hint only, may be inaccurate): {{plant_name}}{{/if}}
{{#if location}}Where the owner says the plant lives (may hint at indoor/outdoor species, do not over-weight): {{location}}{{/if}}

Return JSON matching the schema with className, taxonomicFamily, genus, species, variety, confidence, and nativeRegions.$ps1usr$,
    'handlebars',
    '{"required": [], "optional": ["plant_name", "location"]}',
    ARRAY['bio_section', 'species_id']
);

--
-- Section 2: HEALTH_ASSESSMENT (vision)
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_health_assessment_v1', 1, 'system',
    $ps2sys$You assess the visible condition of a single houseplant from a photo. Do NOT default to "healthy". Actively scan the image for chlorosis, necrosis, crispy edges, wilting, leaf drop, curling, leggy/etiolated growth, pale color, sunburn, mechanical damage, pests or webbing, powdery or sooty coatings, mold, soil crust, rot at base, and pot/substrate issues.

For every finding, consider whichever of these three stressors fit: (1) LIGHT — too little (pale, leggy, small new growth) or too much / wrong quality (bleached patches, sunburn). (2) WATERING with particular attention to OVERWATERING (uniformly yellow lower leaves, soft or mushy stems, persistently wet soil, fungus gnats, wilting despite wet soil, rot). (3) PLACEMENT (heat vents, AC, cold drafts, leaf contact with cold glass, low humidity, no airflow, saucer with standing water, pot too small / too large, hidden behind a curtain). Offer 1–3 plausible causes per finding; do not lock to a single cause.

Only call the plant healthy when there are no visible concerns AND the image is clear enough to judge. If the photo is blurry, cropped, or poorly lit, say the image is insufficient rather than declaring health. Tone: honest and specific, not alarmist, beginner-friendly. Do not produce a full care plan — schedules belong in the dedicated care sections.$ps2sys$,
    'text',
    '{"required": [], "optional": ["species_name", "location"]}',
    ARRAY['bio_section', 'health']
),
(
    'plant_health_assessment_v1', 1, 'user',
    $ps2usr$Assess the visible condition of this plant.
{{#if species_name}}Species (already identified; use as context only, do not re-ID): {{species_name}}{{/if}}
{{#if location}}Owner's placement notes: {{location}}{{/if}}

Return JSON with:
- diagnosis: 2–5 sentences of realistic assessment as described in the system prompt.
- severity: one of NONE, MILD, MODERATE, SEVERE, UNCERTAIN. Use UNCERTAIN when the image is insufficient.
- signs: up to 5 short concrete visible signs (e.g. "crispy tips on lower leaves"). Empty array if none.
- checks: 1–3 short owner-checkable prompts that target whichever of light, watering, or placement is most plausible. Empty array if the plant is clearly healthy.$ps2usr$,
    'handlebars',
    '{"required": [], "optional": ["species_name", "location"]}',
    ARRAY['bio_section', 'health']
);

--
-- Section 3: SPECIES_DESCRIPTION (text)
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_species_description_v1', 1, 'system',
    $ps3sys$You write short neutral encyclopedia-style prose about plant species, in the voice of a Wikipedia article lead. Third person only, no bullets or numbered lists. Cover taxonomy, morphology, native range/ecology at high level, horticultural role, and brief indoor context. Avoid duplicating structured care schedules — those live in dedicated care sections. State uncertainty clearly if the identification is weak.$ps3sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'description']
),
(
    'plant_species_description_v1', 1, 'user',
    $ps3usr$Write an encyclopedia-style description for the following species.

Species: {{species_name}}
{{#if taxonomic_family}}Family: {{taxonomic_family}}{{/if}}
{{#if native_regions}}Native regions: {{native_regions}}{{/if}}

Return JSON with:
- overview: exactly 1–3 paragraphs, each multiple full sentences, separated by two newline characters.
- uses: up to 5 short uses where clearly safe and relevant (culinary, ornamental, medicinal only if non-fringe). Empty array if none apply.$ps3usr$,
    'handlebars',
    '{"required": ["species_name"], "optional": ["taxonomic_family", "native_regions"]}',
    ARRAY['bio_section', 'description']
);

--
-- Section 4: WATER_CARE (text)
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_water_care_v1', 1, 'system',
    $ps4sys$You produce short, practical watering guidance for a single plant. Be conservative: err toward less water for typical houseplants. When geography is provided, reflect local climate (dry/humid, hot/cold seasons). Do not invent specific numeric schedules when the species is not well-characterized.$ps4sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'water']
),
(
    'plant_water_care_v1', 1, 'user',
    $ps4usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Placement notes: {{location}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}

Return JSON with:
- amount: short phrase (e.g. "Water until it drains from the bottom").
- frequency: short phrase (e.g. "Every 7–10 days when the top inch is dry").
- guidance: 2–4 sentences explaining how to know when to water this plant in its context, distinguishing under- vs overwatering cues.$ps4usr$,
    'handlebars',
    '{"required": ["species_name"], "optional": ["growing_context", "location", "geographic_location"]}',
    ARRAY['bio_section', 'water']
);

--
-- Section 5: FERTILIZER_CARE (text)
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_fertilizer_care_v1', 1, 'system',
    $ps5sys$You produce short, practical fertilizer guidance for a single plant. Prefer conservative recommendations for beginners (balanced, diluted, infrequent in winter). Do not recommend specialized regimens unless the species is well-characterized.$ps5sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'fertilizer']
),
(
    'plant_fertilizer_care_v1', 1, 'user',
    $ps5usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}

Return JSON with:
- type: short phrase (e.g. "Balanced liquid fertilizer, diluted to half strength").
- frequency: short phrase (e.g. "Every 4–6 weeks during active growth; pause in winter").
- guidance: 2–4 sentences describing how to feed this plant without burning it, and when to back off.$ps5usr$,
    'handlebars',
    '{"required": ["species_name"], "optional": ["growing_context", "geographic_location"]}',
    ARRAY['bio_section', 'fertilizer']
);

--
-- Section 6: PRUNING_CARE (text)
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_pruning_care_v1', 1, 'system',
    $ps6sys$You produce short, practical pruning guidance for a single plant. "No pruning required" is a valid and often preferred answer. Include WHEN to prune (best season or triggers) and HOW MUCH to remove (conservative). Do not recommend aggressive pruning.$ps6sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'pruning']
),
(
    'plant_pruning_care_v1', 1, 'user',
    $ps6usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}

Return JSON with:
- actionSummary: 2–3 sentences covering WHEN (season or visible triggers) and HOW MUCH to prune for this species. "No pruning required" is valid.
- guidance: 1–2 sentences of general pruning advice for this species.
- generalGuidance: 1–2 sentences of species-typical pruning habits and rationale (do not repeat actionSummary verbatim).$ps6usr$,
    'handlebars',
    '{"required": ["species_name"], "optional": ["growing_context", "geographic_location"]}',
    ARRAY['bio_section', 'pruning']
);

--
-- Section 7: LIGHT_CARE (text)
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_light_care_v1', 1, 'system',
    $ps7sys$You produce short, practical light guidance for a single plant. Describe the brightness level typical for the species and how owners can recognize too-little or too-much light.$ps7sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'light']
),
(
    'plant_light_care_v1', 1, 'user',
    $ps7usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Placement notes: {{location}}{{/if}}

Return JSON with:
- needs: short primary line for the UI (e.g. "Bright indirect light"). One phrase or one short sentence.
- generalGuidance: 2–3 sentences of secondary educational text — what the label means, typical distance from windows, seasonal changes, conservative signs of too much or too little light. Do not repeat the exact wording of needs.$ps7usr$,
    'handlebars',
    '{"required": ["species_name"], "optional": ["growing_context", "location"]}',
    ARRAY['bio_section', 'light']
);

--
-- Section 8: PLACEMENT_CARE (text)
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_placement_care_v1', 1, 'system',
    $ps8sys$You produce short, practical placement guidance for a single plant: where it should live, what environments to avoid, and general room-environment tips. Tailor to the owner's stated location when provided.$ps8sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'placement']
),
(
    'plant_placement_care_v1', 1, 'user',
    $ps8usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Owner's placement notes: {{location}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}

Return JSON with:
- guidance: 1–2 short sentences recommending a specific placement for THIS plant (tailored to the owner's notes when present).
- generalGuidance: 2–3 sentences of general room-environment tips for this species (drafts, humidity, grouping, rotation). Do not repeat the exact wording of guidance.$ps8usr$,
    'handlebars',
    '{"required": ["species_name"], "optional": ["growing_context", "location", "geographic_location"]}',
    ARRAY['bio_section', 'placement']
);

--
-- Section 9: HISTORY_SUMMARY (text) — dedicated bio-section key. Narrative
-- history summary remains served by the existing plant_history_summary_v2
-- pipeline (daily digests). This bio-section prompt produces the single
-- compact "History" paragraph rendered in the bio.
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_history_summary_bio_v1', 1, 'system',
    $ps9sys$You write a short single-paragraph history summary for a plant's bio, drawing only on the timeline facts provided (journal notes, waterings, fertilizings, prunings). Do not invent events. Conservative tone. No bullets; one paragraph. Neutral past-tense observational style; do not offer advice.$ps9sys$,
    'text',
    '{"required": [], "optional": ["species_name", "timeline_text"]}',
    ARRAY['bio_section', 'history']
),
(
    'plant_history_summary_bio_v1', 1, 'user',
    $ps9usr$
{{#if species_name}}Species: {{species_name}}
{{/if}}Timeline facts (newest first):
{{timeline_text}}

Return JSON with:
- summary: one short paragraph (3–6 sentences) describing what has happened with this plant so far. If the timeline is empty, return an empty string.$ps9usr$,
    'handlebars',
    '{"required": ["timeline_text"], "optional": ["species_name"]}',
    ARRAY['bio_section', 'history']
);
