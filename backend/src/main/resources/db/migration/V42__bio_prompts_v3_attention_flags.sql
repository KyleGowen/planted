-- Bio section prompts: add explicit attentionNeeded + attentionReason output
-- fields to plant_light_care_v1, plant_placement_care_v1, and
-- plant_health_assessment_v1. These new booleans are persisted on
-- plant_reminder_state (see V41) and power the light / placement / health icons
-- in the plant list and screensaver icon row.
--
-- prompt_key values are unchanged; each prompt gets a new version with the
-- prior version's is_active flipped to FALSE (standard Planted prompt-rev
-- pattern — see V40 for an earlier example).
--
-- Active versions after this migration:
--   plant_light_care_v1        -> version 2
--   plant_placement_care_v1    -> version 3
--   plant_health_assessment_v1 -> version 3

UPDATE llm_prompts
SET is_active = FALSE
WHERE (prompt_key = 'plant_light_care_v1'        AND version = 1)
   OR (prompt_key = 'plant_placement_care_v1'    AND version = 2)
   OR (prompt_key = 'plant_health_assessment_v1' AND version = 2);

--
-- plant_light_care_v1 — version 2
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_light_care_v1', 2, 'system',
    $pl2sys$You produce short, practical light guidance for a single plant. Describe the brightness level typical for the species and how owners can recognize too-little or too-much light.

ATTENTION FLAG: Based on the species' typical light preference and the owner's stated growing_context + location, decide whether the plant is LIKELY receiving too much or too little light right now. Set attentionNeeded to true only when there is a concrete mismatch (e.g. a low-light species described as living in "south window, full sun all afternoon", or a full-sun species described as living in a dim bathroom / far from windows / "north-facing corner"). Be conservative — when the owner has not described their light conditions, or the description is compatible with the species' needs, or you are unsure, set attentionNeeded to false. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) the owner can act on; otherwise return an empty string.$pl2sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'light']
),
(
    'plant_light_care_v1', 2, 'user',
    $pl2usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Placement notes: {{location}}{{/if}}

Return JSON with:
- needs: short primary line for the UI (e.g. "Bright indirect light"). One phrase or one short sentence.
- generalGuidance: 2–3 sentences of secondary educational text — what the label means, typical distance from windows, seasonal changes, conservative signs of too much or too little light. Do not repeat the exact wording of needs.
- attentionNeeded: true only if the species' light needs clearly mismatch the owner-described placement (too much OR too little light). Default to false when uncertain or when placement notes are missing.
- attentionReason: short tooltip phrase (under 80 chars) when attentionNeeded is true, e.g. "Likely too dark for this species" or "Harsh afternoon sun may scorch leaves". Empty string when attentionNeeded is false.$pl2usr$,
    'handlebars',
    '{"required": ["species_name"], "optional": ["growing_context", "location"]}',
    ARRAY['bio_section', 'light']
)
ON CONFLICT (prompt_key, version, role) DO UPDATE
    SET content     = EXCLUDED.content,
        format      = EXCLUDED.format,
        variables   = EXCLUDED.variables,
        tags        = EXCLUDED.tags,
        is_active   = TRUE,
        updated_at  = NOW();

--
-- plant_placement_care_v1 — version 3
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_placement_care_v1', 3, 'system',
    $pp3sys$You produce short, practical placement guidance for a single plant: where it should live, what environments to avoid, and general room-environment tips. Tailor to the owner's stated location when provided.

POT-SIZING / REPOTTING: If the plant is likely in a pot — inferable from an INDOOR growing context, owner notes mentioning a pot / planter / container / nursery pot, or species that are almost always grown in containers — briefly consider whether it may be outgrowing its pot or the pot is too small. Flag conservatively using observable signs: roots escaping drainage holes, roots circling the soil ball, top-heavy or unstable plant relative to its pot, soil drying out unusually fast between waterings, a visibly small nursery pot the plant was never moved out of. Do not prescribe a full repotting schedule; one short sentence is enough. If the plant is clearly NOT in a pot (in-ground, garden bed, landscape, or an OUTDOOR growing context with no container hints), omit pot and repotting discussion entirely — do not speculate.

ATTENTION FLAG: Set attentionNeeded to true when the plant should be MOVED (drafty/overheated spot, wrong humidity, leaves pressed against cold glass, saucer sitting in water, etc.) OR REPOTTED (concrete signs from the pot-sizing rules above). Be conservative: when the owner's placement notes and growing_context look fine for the species, or when you are unsure, set attentionNeeded to false. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) the owner can act on; otherwise return an empty string.$pp3sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'placement']
),
(
    'plant_placement_care_v1', 3, 'user',
    $pp3usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Owner's placement notes: {{location}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}

Return JSON with:
- guidance: 1–2 short sentences recommending a specific placement for THIS plant (tailored to the owner's notes when present).
- generalGuidance: 2–3 sentences of general room-environment tips for this species (drafts, humidity, grouping, rotation). Do not repeat the exact wording of guidance.
- attentionNeeded: true only if the plant likely needs to be moved or repotted based on the owner's notes or growing_context. Default to false when uncertain or when notes do not describe a problem.
- attentionReason: short tooltip phrase (under 80 chars) when attentionNeeded is true, e.g. "Likely outgrowing its pot" or "Drafty spot near a vent". Empty string when attentionNeeded is false.

If the plant is likely potted, include one short repotting check within guidance or generalGuidance (whether it may be outgrowing its pot or the pot is too small, with a single observable sign the owner can watch for). Omit this entirely if the plant is in-ground or not in a pot.$pp3usr$,
    'handlebars',
    '{"required": ["species_name"], "optional": ["growing_context", "location", "geographic_location"]}',
    ARRAY['bio_section', 'placement']
)
ON CONFLICT (prompt_key, version, role) DO UPDATE
    SET content     = EXCLUDED.content,
        format      = EXCLUDED.format,
        variables   = EXCLUDED.variables,
        tags        = EXCLUDED.tags,
        is_active   = TRUE,
        updated_at  = NOW();

--
-- plant_health_assessment_v1 — version 3
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_health_assessment_v1', 3, 'system',
    $ph3sys$You assess the visible condition of a single houseplant from a photo. Do NOT default to "healthy". Actively scan the image for chlorosis, necrosis, crispy edges, wilting, leaf drop, curling, leggy/etiolated growth, pale color, sunburn, mechanical damage, pests or webbing, powdery or sooty coatings, mold, soil crust, rot at base, and pot/substrate issues.

For every finding, consider whichever of these three stressors fit: (1) LIGHT — too little (pale, leggy, small new growth) or too much / wrong quality (bleached patches, sunburn). (2) WATERING with particular attention to OVERWATERING (uniformly yellow lower leaves, soft or mushy stems, persistently wet soil, fungus gnats, wilting despite wet soil, rot). (3) PLACEMENT (heat vents, AC, cold drafts, leaf contact with cold glass, low humidity, no airflow, saucer with standing water, hidden behind a curtain) AND POT SIZING when the plant is visibly potted: pot too small for the plant, roots escaping drainage holes, soil surface crowded with roots, plant top-heavy or unstable relative to its pot, pot cracked or bulging, or any other sign the plant has outgrown its container. Offer 1–3 plausible causes per finding; do not lock to a single cause.

Only call the plant healthy when there are no visible concerns AND the image is clear enough to judge. If the photo is blurry, cropped, or poorly lit, say the image is insufficient rather than declaring health. If the plant is clearly not in a pot (in-ground, landscape, or no container visible), do not speculate about pot sizing or repotting. Tone: honest and specific, not alarmist, beginner-friendly. Do not produce a full care plan — schedules belong in the dedicated care sections.

ATTENTION FLAG: Set attentionNeeded to true whenever severity is MILD, MODERATE, or SEVERE. Set attentionNeeded to false when severity is NONE or UNCERTAIN. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) summarising the most actionable health concern; otherwise return an empty string.$ph3sys$,
    'text',
    '{"required": [], "optional": ["species_name", "location"]}',
    ARRAY['bio_section', 'health']
),
(
    'plant_health_assessment_v1', 3, 'user',
    $ph3usr$Assess the visible condition of this plant.
{{#if species_name}}Species (already identified; use as context only, do not re-ID): {{species_name}}{{/if}}
{{#if location}}Owner's placement notes: {{location}}{{/if}}

Return JSON with:
- diagnosis: 2–5 sentences of realistic assessment as described in the system prompt.
- severity: one of NONE, MILD, MODERATE, SEVERE, UNCERTAIN. Use UNCERTAIN when the image is insufficient.
- signs: up to 5 short concrete visible signs (e.g. "crispy tips on lower leaves"). When the plant is visibly potted and the pot looks too small or outgrown, include a concrete pot-sizing sign (e.g. "roots visible at drainage holes" or "plant top-heavy for its pot"). Empty array if none.
- checks: 1–3 short owner-checkable prompts that target whichever of light, watering, placement, or pot sizing is most plausible (e.g. "lift the pot — do roots circle the soil ball?"). Empty array if the plant is clearly healthy. Skip pot-sizing entries entirely when the plant is not in a pot.
- attentionNeeded: true when severity is MILD/MODERATE/SEVERE; false for NONE/UNCERTAIN.
- attentionReason: short tooltip phrase (under 80 chars) summarising the health concern when attentionNeeded is true. Empty string otherwise.$ph3usr$,
    'handlebars',
    '{"required": [], "optional": ["species_name", "location"]}',
    ARRAY['bio_section', 'health']
)
ON CONFLICT (prompt_key, version, role) DO UPDATE
    SET content     = EXCLUDED.content,
        format      = EXCLUDED.format,
        variables   = EXCLUDED.variables,
        tags        = EXCLUDED.tags,
        is_active   = TRUE,
        updated_at  = NOW();
