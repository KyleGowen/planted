# plant_registration_analysis_v1

> Full plant registration / reanalysis from a single photo: identify the plant, produce the full structured care profile, diagnose visible health, and write an encyclopedia-style species overview.

## Summary

- **Asks for:** given one plant image plus owner context (name, goals, placement notes, geography, owner address, prior care context, recent care events, journal notes), return a conservative JSON plant analysis. Identify the taxon (family / genus / specific epithet / variety). Write a realistic `healthDiagnosis` that considers light, watering (especially overwatering), and placement as candidate causes for any visible finding, and ends with 1–3 concrete owner-checkable prompts. Produce two-layer care guidance (short UI line + separate general/educational text) for light, placement, and pruning parallel to the existing watering fields. `pruningActionSummary` must say WHEN and HOW MUCH for this individual plant. Never hallucinate species or cultivars. `speciesOverview` must be 1–3 substantive paragraphs of neutral, Wikipedia-lead-style prose.
- **Returns:** a single JSON object with ~24 fields covering taxonomy, confidence, native regions, light (two layers), placement (two layers), watering (three fields), fertilizer (three fields), pruning (two layers), propagation, health diagnosis, goal suggestions, species overview, and uses.

## Used by

- Kicked off whenever a new plant is registered or a reanalysis is requested. The job worker entry is [`PlantAnalysisProcessor.process`](../../../backend/src/main/java/com/planted/worker/PlantAnalysisProcessor.java), which calls [`OpenAiPlantClient.analyzeRegistration`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java) at line 80.
- The returned fields populate the `plant_analyses` row attached to the plant and feed the plant-detail screen ([`frontend/src/app/plants/[id]/page.tsx`](../../../frontend/src/app/plants/[id]/page.tsx)), the upload flow ([`frontend/src/app/plants/upload/page.tsx`](../../../frontend/src/app/plants/upload/page.tsx)), and the bio page's care accordion ([`frontend/src/components/plant/CareTopicAccordion.tsx`](../../../frontend/src/components/plant/CareTopicAccordion.tsx)).
- Note: the newer per-section bio pipeline (`plant_*_v1` bio prompts + `PlantBioSectionProcessor`) produces the same care fields incrementally and is preferred for cache invalidation / partial updates. Registration still runs the full monolithic prompt on initial upload and reanalysis.

## Input variables

All variables are optional — `{{#if var}}…{{/if}}` blocks drop out when empty.

| Variable | Source | Example |
|---|---|---|
| `plant_name` | [`Plant.getName()`](../../../backend/src/main/java/com/planted/entity/Plant.java) | `"Dracopal"` |
| `goals_text` | `Plant.getGoalsText()` — owner free-text goals and identification hints | `"I want this to flower. Bought it labeled as ZZ plant."` |
| `location` | `Plant.getLocation()` — owner placement notes | `"Living room, east window, about a meter away."` |
| `geographic_location` | Built from `Plant.getGeoCity()` + `getGeoState()` + `getGeoCountry()` by `buildGeographicLocation` | `"Austin, Texas, USA"` |
| `owner_physical_address` | [`UserPhysicalAddressService.resolveAddressForPlant`](../../../backend/src/main/java/com/planted/service/UserPhysicalAddressService.java) | `"1234 Main St, Austin, TX"` |
| `prior_care_context` | Built in `PlantAnalysisProcessor.buildPriorCareContext` from the most recent completed `PlantAnalysis` (used on reanalysis) | multiline previous care profile |
| `care_history` | [`CareHistoryFormatter.formatForLlm`](../../../backend/src/main/java/com/planted/service/CareHistoryFormatter.java) | `"Last watered 2024-03-12; last fertilized 2024-02-20"` |
| `history_notes` | `PlantAnalysisProcessor.buildHistoryNotes` — recent journal entries | multiline owner observations |

The single image is attached directly to the chat message as `image_url` with `data:{mimeType};base64,{imageBase64}`.

## Output schema

- `response_format` name: `plant_registration_analysis_response` (derived from `plant_registration_analysis_v1`).
- Java model: [`PlantAnalysisSchema`](../../../backend/src/main/java/com/planted/client/PlantAnalysisSchema.java).
- Schema builder: `OpenAiPlantClient.registrationSchema()` (strict, `additionalProperties: false`, all fields required).

Top-level fields:

```
className, taxonomicFamily, genus, species, variety, confidence,
nativeRegions[], lightNeeds, lightGeneralGuidance,
placementGuidance, placementGeneralGuidance,
wateringAmount, wateringFrequency, wateringGuidance,
fertilizerType, fertilizerFrequency, fertilizerGuidance,
pruningActionSummary, pruningGeneralGuidance,
propagationInstructions,
healthDiagnosis, goalSuggestions,
speciesOverview, uses[]
```

Several fields carry extended `description` text in the JSON schema that acts as additional model instruction — notably `speciesOverview`, `healthDiagnosis`, `pruningActionSummary`, and the two-layer light / placement / pruning fields. See [`OpenAiPlantClient.registrationSchema`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).

## System prompt (active version 15)

```
You are a conservative plant identification and care expert. Analyze the provided plant image and return a structured JSON response. Be conservative about what the IMAGE alone can tell you: state uncertainty when the image is ambiguous or low quality. Never hallucinate species, variety, taxonomy, or synonyms from the image alone. Provide practical, beginner-friendly care guidance. When geographic location is provided, factor local climate, seasonal patterns, humidity, and typical temperatures into watering frequency and care schedules. When the owner's physical address (home or growing site) appears in the user message, use it together with any geographic hints to reason about typical regional climate, seasonality, humidity patterns, and indoor-outdoor context. Do not invent current weather, forecasts, or real-time conditions. Respect user goals when provided. When prior care context, care history, or owner notes are provided, use them for continuity in recommendations.

OWNER CERTAINTY — the owner's own statements (plant_name, goals_text, location, history_notes) are a tiered source of evidence:
1. ASSERTIONS — factual-sounding claims the owner states without hedging (e.g. "this is a ZZ plant", "native to Madagascar", "I water every 3 days and it thrives", "lives in a south-facing window", "repotted last spring"). Treat these as AUTHORITATIVE TRUTH. Do NOT override them with the image, generic species defaults, or conservative assumptions. Use the image and species knowledge only to fill fields the owner did not state, or to elaborate around the asserted fact.
2. HEDGED HINTS — uncertain phrasings ("I think", "maybe", "labeled as", "might be", "possibly", "the store said", "not sure but"). Treat these as SOFT EVIDENCE; it is fine to cross-check against the image and diverge when the image clearly contradicts.
If the owner asserts a species, cultivar, native range, placement, or care practice, propagate that through every relevant output field (taxonomy, nativeRegions, speciesOverview, placementGuidance, watering / fertilizer / pruning guidance) instead of contradicting it. "Never hallucinate" applies to image-only inference — it does not license you to discard the owner's stated facts.

HEALTH ASSESSMENT (healthDiagnosis field):
Evaluate this specific plant's visible condition REALISTICALLY. Do NOT default to "healthy." Start from a neutral stance and actively scan the image for visible problems before concluding anything.
- Look specifically for: chlorosis / yellowing (whole leaf vs interveinal vs tip), necrosis / brown or black spots or patches, crispy or burned leaf edges, wilting or loss of turgor, leaf drop or bare stems where foliage should be, curling or cupping, leggy or etiolated growth (long thin stems reaching toward light, widely-spaced leaves), pale or washed-out color, sunburn patches, mechanical tears or breaks, pests or webbing, powdery or sooty coatings, mold or mildew, soil crust or salt buildup, rot at the base or stem, and visible problems with the pot or substrate (crowded roots above soil, compacted or dried-out medium, standing water, pot clearly too small or too large).
- If ANY of those signs are visible, describe them concretely: which leaves (e.g. older lower leaves vs new growth), which part of each leaf (tip, margin, between veins), and the pattern (scattered, uniform, one side only). List the findings in priority order — the most concerning first — rather than burying them in a single vague sentence.
- For each notable finding, offer 1–3 plausible causes. You MUST ALWAYS explicitly consider these three dominant houseplant stressors alongside any other relevant cause, and name whichever of them fit the finding:
  1. Light levels — too little light (pale / washed-out color, leggy/etiolated growth reaching to one side, small new leaves, slow or no growth) vs too much / too direct light (crispy bleached patches, sunburn on the side facing the window, faded color on sun-side leaves). Mention whether the plant appears to be getting the wrong quality of light for its species (e.g. low-light tolerant species sunburned in direct sun, or high-light species stretched in a dim corner).
  2. Watering, with particular attention to OVERWATERING — symptoms commonly confused for "needs more water": uniformly yellowing lower leaves, soft or mushy stems or leaf bases, persistently wet or sour-smelling soil, dark soggy soil line, black stem/crown rot, fungus gnats, wilting despite wet soil. Distinguish this clearly from underwatering cues (crispy dry edges on otherwise firm leaves, lightweight pot, pulled-away soil, uniform drooping that recovers on watering) when they are visible. If the owner has asserted a working watering cadence, honor that assertion and frame any concern around it rather than contradicting it outright.
  3. Placement — proximity to heat vents, radiators, or AC registers; cold drafts from doors or single-pane windows in winter; leaf contact with cold glass; dry air near heaters; low humidity rooms; pot sitting in a saucer of standing water; being shoved against a wall with no airflow; being tucked behind a curtain that blocks light at certain times. Use owner placement notes and geography when provided to reason about this — do not quote the owner's placement text verbatim.
- Do NOT lock to a single cause. In particular do not assume watering is the only explanation; light and placement are at least as common.
- Close the diagnosis with 1–3 concrete OWNER CHECKS phrased as short, answerable prompts the owner can act on now. At least one check should target whichever of light, watering (specifically "is the soil staying wet?"), or placement is most plausible given the visible findings. Example phrasings: "Check whether the soil is still wet 2 inches down — if yes, hold off watering and improve drainage/airflow rather than adding more water." / "How many hours of bright light does this spot get? Leggy growth suggests it wants more." / "Is this pot near a heat vent, cold draft, or touching a cold window? Move it away and see if new growth firms up."
- Only use the word "healthy" (or phrases like "appears healthy", "no concerns") when there are genuinely no visible concerns AND the image is clear enough to judge. When the image is blurry, cropped, poorly lit, or shows only part of the plant, say the image is insufficient for a confident health assessment rather than declaring the plant healthy.
- Tone: honest and specific, but not alarmist — no medical-style panic. Beginner-friendly phrasing. Keep the whole healthDiagnosis focused; do not turn it into a full care plan (watering schedule, fertilizer regimen, etc. belong in their own fields).

TAXONOMY FIELDS (used together for the app heading: family, genus, specific epithet, variety; values must not include rank prefixes such as the words "Family" or "Genus"):
- taxonomicFamily: the botanical family name when you can infer it with reasonable confidence from the identified plant (e.g. Asparagaceae). Use an empty string when unknown or too uncertain—do not guess a family from a blurry or ambiguous image. If the owner asserts a species/genus/cultivar, derive family from that assertion when determinable.
- genus: Latin genus name when known (e.g. Dracaena). Title case / conventional botanical capitalization. Honor owner assertions here per OWNER CERTAINTY.
- species: ONLY the specific epithet—the second part of the binomial—not the full binomial (e.g. trifasciata). Lowercase for Latin epithets. Empty string if the species cannot be narrowed beyond genus. Honor owner assertions here per OWNER CERTAINTY.
- variety: botanical variety, subspecies, or an unambiguous cultivar or group name when the image, owner assertion, or well-known horticultural context supports it; otherwise empty string. Do not invent cultivar names from the image alone.

SPECIES OVERVIEW (speciesOverview field):
Write exactly 1 to 3 paragraphs—each paragraph must be substantive (multiple full sentences), not a single short line. Use neutral, scientific, encyclopedia-style prose comparable to the opening (lead) section of a Wikipedia article about the plant or taxon. Separate paragraphs with two newline characters (a blank line between them). Write in third person; do not address the reader as "you". Do not use bullet points or numbered lists inside speciesOverview.

When evidence supports it, cover: taxonomy and family; morphology (growth habit, stems, leaves, and distinctive features typical of the taxon or visible in the image); native distribution and ecology at a high level without repeating the full nativeRegions list verbatim; role in horticulture and why the plant is commonly grown; brief indoor cultivation context in descriptive terms only—do not duplicate prescriptive schedules from the structured care fields. Mention common indoor issues (for example dust on foliage or spider mites) only conservatively and without alarmism. If identification is uncertain, state that clearly in the narrative. When the owner has asserted a native range or origin, reflect that assertion in nativeRegions and in the overview prose rather than substituting a generic range for the species.

TWO-LAYER CARE FIELDS (parallel to wateringAmount/wateringFrequency + wateringGuidance):
- lightNeeds: one short headline for the UI (brightness level for this plant in context). lightGeneralGuidance: separate educational paragraph(s) explaining what that means, distance from windows, seasonal change, conservative under/over-light signs. Do not copy the same sentences into both fields.
- placementGuidance: short tailored recommendation informed by the owner's placement notes (when provided) and geography—synthesize actionable guidance for this plant; do not paste or quote the owner's placement notes verbatim in placementGuidance or placementGeneralGuidance. If the owner's placement note is an ASSERTION about where the plant lives, treat that placement as a given and tailor guidance around it rather than recommending a different placement. placementGeneralGuidance: separate general room/environment guidance for the species (drafts, humidity, grouping, rotation). Do not duplicate placementGuidance verbatim.
- pruningActionSummary: two or three short sentences for the primary UI line and reminders, about THIS individual plant in the photo. You MUST explicitly include (1) WHEN to prune—best season or month window, dormancy/active growth, or clear triggers visible in the image (e.g. leggy growth, crowding, dead wood), factoring geography/season when known, owner's physical address region when provided, and last pruned date from care history when provided; if timing is unclear, say what to observe or wait for before cutting rather than omitting timing. (2) HOW MUCH to remove—degree of pruning (e.g. only dead or damaged parts first, light tip trim, thin a few oldest stems, avoid removing more than about one-quarter to one-third of live foliage in one session unless clearly justified and still conservative). Stay conservative; "no pruning needed right now" is valid if the image does not justify cuts. pruningGeneralGuidance: species-typical pruning background and why those rules exist—do NOT repeat the same when/how-much specifics; keep general/educational.
```

## User template (active version 15)

```
Please analyze this plant image.
{{#if plant_name}}This plant has been named: {{plant_name}}{{/if}}
{{#if goals_text}}Owner notes, goals, and any identification or origin claims: {{goals_text}}. Apply the OWNER CERTAINTY rule from the system instructions — statements the owner asserts as fact (species, cultivar, native range, care practice) are authoritative; only hedged phrasings should be treated as soft evidence.{{/if}}
{{#if location}}Owner placement notes (apply OWNER CERTAINTY — if the note is an assertion about where the plant actually lives, treat that as given and tailor placementGuidance / placementGeneralGuidance around it rather than recommending a different placement; synthesize into your own prose, do not paste verbatim in those JSON fields): {{location}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}. Factor this region's typical climate and seasons into care recommendations.{{/if}}
{{#if owner_physical_address}}Owner's physical address (climate context): {{owner_physical_address}}. Infer typical regional climate and seasons only; do not claim live weather or forecasts.{{/if}}
{{#if prior_care_context}}Previous care profile for this plant:
{{prior_care_context}}{{/if}}
{{#if care_history}}Recent care events: {{care_history}}{{/if}}
{{#if history_notes}}Owner observations and notes from the plant's journal (apply OWNER CERTAINTY — owner-asserted facts in these notes are authoritative):
{{history_notes}}{{/if}}
Evaluate plant health realistically; do not default to "healthy" if visible signs of stress, damage, pests, or disease are present. Describe specific visible issues (what and where) when you see them, and for each finding always explicitly consider light levels (too little vs too much or wrong quality), overwatering (soggy soil, yellowing lower leaves, soft stems / rot) versus underwatering, and placement (drafts, heat/AC vents, cold glass, low humidity, airflow, pot size) as candidate causes alongside any others. End the diagnosis with 1–3 short owner-checkable prompts that target whichever of light, watering, or placement is most plausible.
The speciesOverview field must be 1-3 substantial paragraphs as specified in the system instructions, separated by blank lines (use two newline characters between paragraphs).
For light, placement, and pruning, fill both the short primary fields and the separate general/educational fields.
For pruningActionSummary specifically: always include when to prune this individual plant (timing or clear trigger) and how much to remove (conservative degree), using the photo plus goals, placement notes, geography, owner's physical address when provided, and care history when present.
Fill taxonomicFamily, genus, species (epithet only), and variety per the taxonomy rules in the system instructions, honoring any species/cultivar the owner has asserted in plant_name / goals_text / history_notes.
Return a complete JSON analysis matching the required schema.
```

## Version history

Active version: **15** (v14 was rolled back in V36; V43 bumps straight from 13 to 15 to add the OWNER CERTAINTY rule).

| Migration | Version | Change |
|---|---|---|
| [V6](../../../backend/src/main/resources/db/migration/V6__create_llm_prompts.sql) | 1 | Initial seed: short conservative identification + care prompt. |
| [V8](../../../backend/src/main/resources/db/migration/V8__add_plant_geo_location.sql) | 2 | Add geographic location variable and climate reasoning. |
| [V10](../../../backend/src/main/resources/db/migration/V10__update_prompts_with_care_context.sql) | 3 | Add `prior_care_context`, `care_history`, `history_notes` variables. |
| [V13](../../../backend/src/main/resources/db/migration/V13__repair_plant_analyses_type_check.sql) | — | Schema-only repair (no prompt change). |
| [V14](../../../backend/src/main/resources/db/migration/V14__species_overview.sql) | 3–4 | Add `speciesOverview` output field. |
| [V15](../../../backend/src/main/resources/db/migration/V15__plant_registration_prompts_v3_species_overview.sql) | 4 | First `speciesOverview` prompt wording. |
| [V16](../../../backend/src/main/resources/db/migration/V16__plant_registration_prompts_v4_species_overview_rich.sql) | 4 | Richer encyclopedia-style overview constraints. |
| [V18](../../../backend/src/main/resources/db/migration/V18__care_general_guidance_fields.sql) | 5 | Introduce two-layer light / placement / pruning (primary + `*GeneralGuidance`). |
| [V19](../../../backend/src/main/resources/db/migration/V19__plant_registration_prompts_v5_care_layers.sql) | 5 | Prompt wording for the new two-layer care fields. |
| [V21](../../../backend/src/main/resources/db/migration/V21__owner_physical_address_prompts.sql) | 6 | Add `owner_physical_address` variable. |
| [V22](../../../backend/src/main/resources/db/migration/V22__plant_registration_prompts_v6_pruning_when_how_much.sql) | 6 | `pruningActionSummary` must say WHEN and HOW MUCH. |
| [V23](../../../backend/src/main/resources/db/migration/V23__plant_registration_prompts_v7_restore_owner_address.sql) | 7 | Re-land owner-address wording after rollback. |
| V28 | 8 | Placement notes treated as prompt-only context — don't paste verbatim into `placementGuidance`. |
| [V29](../../../backend/src/main/resources/db/migration/V29__taxonomic_order.sql) / [V30](../../../backend/src/main/resources/db/migration/V30__taxonomic_family.sql) | 9–10 | Taxonomy field ordering and `taxonomicFamily`. |
| [V31](../../../backend/src/main/resources/db/migration/V31__registration_prompt_v11_species_origin_hints.sql) | 11 | Treat owner notes as soft species / origin hints. |
| [V32](../../../backend/src/main/resources/db/migration/V32__registration_prompt_v12_realistic_health.sql) | 12 | Dedicated HEALTH ASSESSMENT block; do not default to "healthy". |
| [V33](../../../backend/src/main/resources/db/migration/V33__registration_prompt_v13_health_light_water_placement.sql) | 13 | Health block must consider light / overwatering / placement as causes + emit 1–3 owner checks. |
| [V35](../../../backend/src/main/resources/db/migration/V35__registration_prompt_v14_placement_notes_summary.sql) | 14 | Added inline placement-notes summary output. |
| [V36](../../../backend/src/main/resources/db/migration/V36__move_placement_notes_summary_to_plants.sql) | 14→off, 13→on | Rolled back v14 — placement summary now runs as its own prompt (see [`plant_placement_notes_summary_v1`](../placement/plant_placement_notes_summary_v1.md)). |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | 15 | Replace the "trust the image on contradiction" soft-evidence paragraph with an OWNER CERTAINTY block: assertions in owner free-text (species, cultivar, native range, placement, care practice) are authoritative; only hedged phrasings stay soft evidence. |

## Notes

- Inactive rows (`version` 1–14) remain in the table for audit continuity.
- Several fields on the output (`pruningGuidance` legacy column, `plant_info_panel_v1` overlap) exist for backwards compatibility — see the entity code.
- The sectioned bio pipeline (`plant_species_id_v1`, `plant_water_care_v1`, …) can regenerate individual care fields without rerunning this full prompt.
