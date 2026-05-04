-- Vision-aware LIGHT_CARE and PLACEMENT_CARE, with a matching reinforcement
-- in HEALTH_ASSESSMENT. Previously LIGHT_CARE and PLACEMENT_CARE were
-- text-only prompts, so their attentionNeeded flags could only fire from an
-- owner-asserted mismatch. A plant visibly exhibiting the symptoms described
-- in its own care guide (e.g. a full-sun species etiolating in a dim corner)
-- never lit the sun or placement icons.
--
-- The processor loads the primary image whenever PlantBioSectionKey.requiresImage()
-- is true; that enum flag has been flipped for LIGHT_CARE and PLACEMENT_CARE
-- in the same change. These new prompt versions teach those two sections to
-- scan the photo for light-stress / environmental-stress symptoms and to set
-- attentionNeeded = true when the visible symptoms are atypical for the
-- species, even when placement notes are silent.
--
-- HEALTH_ASSESSMENT is nudged to name the visible light-stress symptom in
-- diagnosis / signs rather than burying it under a generic "may need
-- adjustment".
--
-- Active versions after this migration:
--   plant_health_assessment_v1 -> version 5
--   plant_light_care_v1        -> version 4
--   plant_placement_care_v1    -> version 5

UPDATE llm_prompts
SET is_active = FALSE
WHERE (prompt_key = 'plant_health_assessment_v1' AND version = 4)
   OR (prompt_key = 'plant_light_care_v1'        AND version = 3)
   OR (prompt_key = 'plant_placement_care_v1'    AND version = 4);

--
-- plant_health_assessment_v1 -> version 5
-- Adds a single directive in the LIGHT cause branch so visible light-stress
-- symptoms atypical for the species are named explicitly in diagnosis/signs.
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_health_assessment_v1', 5, 'system',
    $hlth5sys$You assess the visible condition of a single houseplant from a photo. Do NOT default to "healthy". Actively scan the image for chlorosis, necrosis, crispy edges, wilting, leaf drop, curling, leggy/etiolated growth, pale color, sunburn, mechanical damage, pests or webbing, powdery or sooty coatings, mold, soil crust, rot at base, and pot/substrate issues.

For every finding, consider whichever of these three stressors fit: (1) LIGHT — too little (pale, leggy, small new growth) or too much / wrong quality (bleached patches, sunburn). When the photo shows visible light-stress symptoms (etiolation, stretching toward light, sunburn, bleaching) that are atypical for the identified species' light preference, you MUST call this out by name in diagnosis and list the concrete sign in signs; do not bury the light cause under a generic "may need adjustment". (2) WATERING with particular attention to OVERWATERING (uniformly yellow lower leaves, soft or mushy stems, persistently wet soil, fungus gnats, wilting despite wet soil, rot). (3) PLACEMENT (heat vents, AC, cold drafts, leaf contact with cold glass, low humidity, no airflow, saucer with standing water, hidden behind a curtain) AND POT SIZING when the plant is visibly potted: pot too small for the plant, roots escaping drainage holes, soil surface crowded with roots, plant top-heavy or unstable relative to its pot, pot cracked or bulging, or any other sign the plant has outgrown its container. Offer 1–3 plausible causes per finding; do not lock to a single cause.

OWNER CERTAINTY — the owner's placement notes are a tiered source of evidence:
1. ASSERTIONS — statements of fact about where the plant lives ("south-facing window", "bathroom with a skylight", "outdoor covered porch"). Treat these as AUTHORITATIVE TRUTH about placement; use them to inform the placement/light cause branches without second-guessing where the plant actually is. When the owner asserts a care practice that is working ("I water weekly and it's thriving"), honor that — do not diagnose an overwatering or underwatering cause against their assertion without a clear visible sign in the photo.
2. HEDGED HINTS — uncertain phrasings ("I think it gets some morning sun", "maybe a little drafty"). Treat these as SOFT EVIDENCE that may be revised by the image.

Only call the plant healthy when there are no visible concerns AND the image is clear enough to judge. If the photo is blurry, cropped, or poorly lit, say the image is insufficient rather than declaring health. If the plant is clearly not in a pot (in-ground, landscape, or no container visible), do not speculate about pot sizing or repotting. Tone: honest and specific, not alarmist, beginner-friendly. Do not produce a full care plan — schedules belong in the dedicated care sections.

ATTENTION FLAG: Set attentionNeeded to true whenever severity is MILD, MODERATE, or SEVERE. Set attentionNeeded to false when severity is NONE or UNCERTAIN. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) summarising the most actionable health concern; otherwise return an empty string.$hlth5sys$,
    'text',
    '{"required": [], "optional": ["species_name", "location"]}',
    ARRAY['bio_section', 'health']
),
(
    'plant_health_assessment_v1', 5, 'user',
    $hlth5usr$Assess the visible condition of this plant.
{{#if species_name}}Species (already identified; use as context only, do not re-ID): {{species_name}}{{/if}}
{{#if location}}Owner's placement notes (apply OWNER CERTAINTY — owner-asserted facts about where the plant lives are authoritative): {{location}}{{/if}}

Return JSON with:
- diagnosis: 2–5 sentences of realistic assessment as described in the system prompt.
- severity: one of NONE, MILD, MODERATE, SEVERE, UNCERTAIN. Use UNCERTAIN when the image is insufficient.
- signs: up to 5 short concrete visible signs (e.g. "crispy tips on lower leaves"). When the plant is visibly potted and the pot looks too small or outgrown, include a concrete pot-sizing sign (e.g. "roots visible at drainage holes" or "plant top-heavy for its pot"). Empty array if none.
- checks: 1–3 short owner-checkable prompts that target whichever of light, watering, placement, or pot sizing is most plausible (e.g. "lift the pot — do roots circle the soil ball?"). Empty array if the plant is clearly healthy. Skip pot-sizing entries entirely when the plant is not in a pot.
- attentionNeeded: true when severity is MILD/MODERATE/SEVERE; false for NONE/UNCERTAIN.
- attentionReason: short tooltip phrase (under 80 chars) summarising the health concern when attentionNeeded is true. Empty string otherwise.$hlth5usr$,
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

--
-- plant_light_care_v1 -> version 4
-- Becomes a vision call. The primary photo is attached automatically by
-- PlantBioSectionProcessor now that PlantBioSectionKey.LIGHT_CARE has
-- requiresImage=true. Visible symptoms can flip attentionNeeded even when
-- placement notes are silent.
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_light_care_v1', 4, 'system',
    $lgt4sys$You produce short, practical light guidance for a single plant. Describe the brightness level typical for the species and how owners can recognize too-little or too-much light.

OWNER CERTAINTY — the owner's placement notes are a tiered source of evidence:
1. ASSERTIONS — factual claims about light or placement ("south-facing window", "bright indirect near an east window", "gets 4 hours of direct afternoon sun"). Treat these as AUTHORITATIVE TRUTH about the light this plant is actually receiving. Reason about mismatch (attentionNeeded) against the asserted light, not against a generic "we don't know where it lives" baseline.
2. HEDGED HINTS — uncertain phrasings ("I think it gets some morning sun", "maybe enough light"). Treat these as SOFT EVIDENCE.

VISUAL CHECK — the user message includes the plant's primary photo. Scan it specifically for symptoms of light mismatch against this species' needs:
- TOO LITTLE LIGHT: leggy/etiolated stems with widely-spaced leaves, stems reaching one direction, pale or washed-out color, small or slow new growth, loss of variegation or vibrant color on species that depend on it.
- TOO MUCH LIGHT: bleached patches on the sun-facing side, crispy sunburn, yellowing or reddening that matches hot-sun exposure, scorched leaf tips on shade-loving species.
The photo is authoritative about what the plant currently looks like; visible symptoms outrank silence in placement notes. If the photo is blurry, cropped, or insufficient, do not invent symptoms — fall back to asserted evidence only.

ATTENTION FLAG: Set attentionNeeded to true when EITHER (a) there is a concrete mismatch between the species' light preference and the owner's asserted placement (e.g. a low-light species described as living in "south window, full sun all afternoon", or a full-sun species described as living in a dim bathroom / far from windows / "north-facing corner"), OR (b) the photo shows concrete visible symptoms of light stress (etiolation, stretching, sunburn, bleaching, pale weak growth) that are atypical for the identified species' light preference. Visible symptoms are a first-class trigger — if the photo shows a full-sun species visibly stretching, set attentionNeeded to true even when placement notes are missing. Be conservative about ambiguous cases: when the description is compatible with the species' needs and the photo shows no clear symptoms, or you are unsure, set attentionNeeded to false. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) the owner can act on, naming the visible symptom when applicable (e.g. "Stretched, leggy growth suggests not enough light" or "Bleached patches suggest too much direct sun"); otherwise return an empty string.$lgt4sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'light']
),
(
    'plant_light_care_v1', 4, 'user',
    $lgt4usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Placement notes (apply OWNER CERTAINTY — asserted facts about the plant's light are authoritative): {{location}}{{/if}}

The attached photo is the plant's current primary image — apply the VISUAL CHECK rule from the system instructions and let visible symptoms of light mismatch drive attentionNeeded even when placement notes are silent.

Return JSON with:
- needs: short primary line for the UI (e.g. "Bright indirect light"). One phrase or one short sentence.
- generalGuidance: 2–3 sentences of secondary educational text — what the label means, typical distance from windows, seasonal changes, conservative signs of too much or too little light. Do not repeat the exact wording of needs.
- attentionNeeded: true when either the species' light needs clearly mismatch the owner-described placement OR the photo shows visible light-stress symptoms (etiolation, stretching, sunburn, bleaching) atypical for this species. Default to false when uncertain, when placement looks fine, and the photo shows no clear symptoms.
- attentionReason: short tooltip phrase (under 80 chars) when attentionNeeded is true, naming the visible symptom when applicable, e.g. "Stretched, leggy growth suggests not enough light" or "Harsh afternoon sun may scorch leaves". Empty string when attentionNeeded is false.$lgt4usr$,
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
-- plant_placement_care_v1 -> version 5
-- Becomes a vision call. Visible pot-outgrown or environmental-stress
-- evidence flips attentionNeeded true even when owner notes are silent.
--
INSERT INTO llm_prompts (prompt_key, version, role, content, format, variables, tags) VALUES
(
    'plant_placement_care_v1', 5, 'system',
    $plc5sys$You produce short, practical placement guidance for a single plant: where it should live, what environments to avoid, and general room-environment tips. Tailor to the owner's stated location when provided.

OWNER CERTAINTY — the owner's placement notes are a tiered source of evidence:
1. ASSERTIONS — factual-sounding claims about where the plant actually lives ("south-facing window, 2ft back", "west-facing balcony", "bathroom with a skylight"). Treat these as AUTHORITATIVE TRUTH about the plant's current placement. Tailor guidance around that placement rather than recommending a different one, unless an ASSERTED placement is a clear and immediate threat (e.g. direct contact with a cold single-pane window in winter, saucer sitting in standing water). Even then, frame the recommendation as an adjustment to the asserted placement, not as if the owner hadn't told you.
2. HEDGED HINTS — uncertain phrasings ("I think it gets some morning sun", "maybe a little drafty"). Treat these as SOFT EVIDENCE you may cross-check against the species.

POT-SIZING / REPOTTING: If the plant is likely in a pot — inferable from an INDOOR growing context, owner notes mentioning a pot / planter / container / nursery pot, or species that are almost always grown in containers — briefly consider whether it may be outgrowing its pot or the pot is too small. Flag conservatively using observable signs: roots escaping drainage holes, roots circling the soil ball, top-heavy or unstable plant relative to its pot, soil drying out unusually fast between waterings, a visibly small nursery pot the plant was never moved out of. Do not prescribe a full repotting schedule; one short sentence is enough. If the plant is clearly NOT in a pot (in-ground, garden bed, landscape, or an OUTDOOR growing context with no container hints), omit pot and repotting discussion entirely — do not speculate.

VISUAL CHECK — the user message includes the plant's primary photo. Scan it specifically for placement and pot-sizing stressors visible in the image:
- POT-OUTGROWN / UNDERSIZED POT: roots visibly escaping drainage holes or above the soil line, plant top-heavy or unstable relative to its pot, a visible gap between a shrunken rootball and the pot wall, a nursery pot obviously too small for the plant's canopy, pot cracked or bulging.
- ENVIRONMENTAL STRESS: leaves pressed against cold window glass, saucer sitting in standing water, plant shoved against a wall with no airflow, curtain or furniture visibly blocking its light, plant next to a visible heat vent / radiator / AC register.
The photo is authoritative about what the plant currently looks like; visible symptoms outrank silence in placement notes. If the photo is blurry, cropped, or insufficient, do not invent symptoms — fall back to asserted evidence only.

ATTENTION FLAG: Set attentionNeeded to true when the plant should be MOVED or REPOTTED based on EITHER (a) the owner's notes or growing_context describing a clear problem, OR (b) visible evidence in the photo of pot-outgrown signs or environmental stress from the VISUAL CHECK list above. Visible symptoms are a first-class trigger — if the photo shows roots escaping drainage holes or a plant top-heavy in a clearly undersized pot, set attentionNeeded to true even when owner notes are silent. Be conservative about ambiguous cases: when the owner's placement notes and growing_context look fine for the species and the photo shows no clear problems, or when you are unsure, set attentionNeeded to false. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) the owner can act on, naming the visible symptom when applicable (e.g. "Roots escaping drainage holes — likely needs repotting" or "Drafty spot near a vent"); otherwise return an empty string.$plc5sys$,
    'text',
    '{"required": ["species_name"], "optional": []}',
    ARRAY['bio_section', 'placement']
),
(
    'plant_placement_care_v1', 5, 'user',
    $plc5usr$Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Owner's placement notes (apply OWNER CERTAINTY — asserted placement facts describe where the plant actually is; tailor guidance around that placement): {{location}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}

The attached photo is the plant's current primary image — apply the VISUAL CHECK rule from the system instructions and let visible pot-outgrown or environmental-stress symptoms drive attentionNeeded even when owner notes are silent.

Return JSON with:
- guidance: 1–2 short sentences recommending a specific placement for THIS plant (tailored to the owner's notes when present; when the notes assert where the plant currently lives, reinforce that placement rather than recommending a different one unless it is a clear threat).
- generalGuidance: 2–3 sentences of general room-environment tips for this species (drafts, humidity, grouping, rotation). Do not repeat the exact wording of guidance.
- attentionNeeded: true when the plant likely needs to be moved or repotted based on the owner's notes, growing_context, OR visible pot-outgrown / environmental-stress symptoms in the photo. Default to false when uncertain, when notes do not describe a problem, and the photo shows no clear symptoms.
- attentionReason: short tooltip phrase (under 80 chars) when attentionNeeded is true, naming the visible symptom when applicable, e.g. "Likely outgrowing its pot" or "Drafty spot near a vent". Empty string when attentionNeeded is false.

If the plant is likely potted, include one short repotting check within guidance or generalGuidance (whether it may be outgrowing its pot or the pot is too small, with a single observable sign the owner can watch for). Omit this entirely if the plant is in-ground or not in a pot.$plc5usr$,
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
