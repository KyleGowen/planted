-- Bio section prompts v2: add pot-sizing / repotting awareness to
-- plant_placement_care_v1 and plant_health_assessment_v1.
--
-- Placement care (text-only): if the plant is likely potted (indoor growing
-- context, owner notes referencing a pot/planter/container, or species-typical
-- indoor cultivation), comment conservatively on whether it may be outgrowing
-- its pot. If it is clearly in-ground (outdoor bed/garden/landscape), skip
-- repotting entirely.
--
-- Health assessment (vision): promote pot-sizing from a passing mention inside
-- the PLACEMENT stressor bullet to an explicit concrete finding the model must
-- actively scan for. Still skip for plants that aren't in pots.
--
-- No schema changes — repotting content is woven into existing prose fields
-- (placement `guidance`/`generalGuidance`; health `diagnosis`/`signs`/`checks`).

UPDATE llm_prompts
SET is_active = FALSE
WHERE prompt_key IN ('plant_placement_care_v1', 'plant_health_assessment_v1')
  AND version = 1;

--
-- plant_placement_care_v1 — version 2
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_placement_care_v1', 2, 'system',
    $pp2sys$You produce short, practical placement guidance for a single plant: where it should live, what environments to avoid, and general room-environment tips. Tailor to the owner's stated location when provided.

POT-SIZING / REPOTTING: If the plant is likely in a pot — inferable from an INDOOR growing context, owner notes mentioning a pot / planter / container / nursery pot, or species that are almost always grown in containers — briefly consider whether it may be outgrowing its pot or the pot is too small. Flag conservatively using observable signs: roots escaping drainage holes, roots circling the soil ball, top-heavy or unstable plant relative to its pot, soil drying out unusually fast between waterings, a visibly small nursery pot the plant was never moved out of. Do not prescribe a full repotting schedule; one short sentence is enough. If the plant is clearly NOT in a pot (in-ground, garden bed, landscape, or an OUTDOOR growing context with no container hints), omit pot and repotting discussion entirely — do not speculate.$pp2sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'placement']
),
(
    'plant_placement_care_v1', 2, 'user',
    $pp2usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Owner's placement notes: {{location}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}

Return JSON with:
- guidance: 1–2 short sentences recommending a specific placement for THIS plant (tailored to the owner's notes when present).
- generalGuidance: 2–3 sentences of general room-environment tips for this species (drafts, humidity, grouping, rotation). Do not repeat the exact wording of guidance.

If the plant is likely potted, include one short repotting check within guidance or generalGuidance (whether it may be outgrowing its pot or the pot is too small, with a single observable sign the owner can watch for). Omit this entirely if the plant is in-ground or not in a pot.$pp2usr$,
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
-- plant_health_assessment_v1 — version 2
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_health_assessment_v1', 2, 'system',
    $ph2sys$You assess the visible condition of a single houseplant from a photo. Do NOT default to "healthy". Actively scan the image for chlorosis, necrosis, crispy edges, wilting, leaf drop, curling, leggy/etiolated growth, pale color, sunburn, mechanical damage, pests or webbing, powdery or sooty coatings, mold, soil crust, rot at base, and pot/substrate issues.

For every finding, consider whichever of these three stressors fit: (1) LIGHT — too little (pale, leggy, small new growth) or too much / wrong quality (bleached patches, sunburn). (2) WATERING with particular attention to OVERWATERING (uniformly yellow lower leaves, soft or mushy stems, persistently wet soil, fungus gnats, wilting despite wet soil, rot). (3) PLACEMENT (heat vents, AC, cold drafts, leaf contact with cold glass, low humidity, no airflow, saucer with standing water, hidden behind a curtain) AND POT SIZING when the plant is visibly potted: pot too small for the plant, roots escaping drainage holes, soil surface crowded with roots, plant top-heavy or unstable relative to its pot, pot cracked or bulging, or any other sign the plant has outgrown its container. Offer 1–3 plausible causes per finding; do not lock to a single cause.

Only call the plant healthy when there are no visible concerns AND the image is clear enough to judge. If the photo is blurry, cropped, or poorly lit, say the image is insufficient rather than declaring health. If the plant is clearly not in a pot (in-ground, landscape, or no container visible), do not speculate about pot sizing or repotting. Tone: honest and specific, not alarmist, beginner-friendly. Do not produce a full care plan — schedules belong in the dedicated care sections.$ph2sys$,
    'text',
    '{"required": [], "optional": ["species_name", "location"]}',
    ARRAY['bio_section', 'health']
),
(
    'plant_health_assessment_v1', 2, 'user',
    $ph2usr$Assess the visible condition of this plant.
{{#if species_name}}Species (already identified; use as context only, do not re-ID): {{species_name}}{{/if}}
{{#if location}}Owner's placement notes: {{location}}{{/if}}

Return JSON with:
- diagnosis: 2–5 sentences of realistic assessment as described in the system prompt.
- severity: one of NONE, MILD, MODERATE, SEVERE, UNCERTAIN. Use UNCERTAIN when the image is insufficient.
- signs: up to 5 short concrete visible signs (e.g. "crispy tips on lower leaves"). When the plant is visibly potted and the pot looks too small or outgrown, include a concrete pot-sizing sign (e.g. "roots visible at drainage holes" or "plant top-heavy for its pot"). Empty array if none.
- checks: 1–3 short owner-checkable prompts that target whichever of light, watering, placement, or pot sizing is most plausible (e.g. "lift the pot — do roots circle the soil ball?"). Empty array if the plant is clearly healthy. Skip pot-sizing entries entirely when the plant is not in a pot.$ph2usr$,
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
