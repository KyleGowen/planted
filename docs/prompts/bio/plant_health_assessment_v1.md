# plant_health_assessment_v1

> Bio section: realistic photo-driven health read that must consider light, watering (especially overwatering), and placement as candidate causes. Does NOT default to "healthy". Does not produce a full care plan. Also emits an `attentionNeeded` boolean that drives the health icon on the plant list + screensaver icon row.

## Summary

- **Asks for:** scan the photo for visible stressors (chlorosis, necrosis, leggy growth, sunburn, rot, pests, soil / pot issues). For every finding, offer 1–3 plausible causes across light / watering / placement. Only say "healthy" when there are genuinely no concerns AND the image is clear. Suggest 1–3 owner-checkable prompts that target the most plausible stressor.
- **Returns:** `{ diagnosis, severity, signs[], checks[], attentionNeeded, attentionReason }` with `severity` ∈ `NONE | MILD | MODERATE | SEVERE | UNCERTAIN`. `attentionNeeded` mirrors `severity` (true for MILD/MODERATE/SEVERE, false otherwise); `attentionReason` is a short tooltip-length phrase when true, empty string otherwise.

## Used by

- [`HealthAssessmentStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/HealthAssessmentStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) → [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- Uses the plant's primary image (vision call).
- `attentionNeeded` + `attentionReason` read by [`PlantReminderService.syncBioAttention`](../../../backend/src/main/java/com/planted/service/PlantReminderService.java) and persisted onto `plant_reminder_state.health_attention_needed` / `health_attention_reason`.

## Input variables

All optional.

| Variable | Source |
|---|---|
| `species_name` | [`BioSectionContext.speciesName`](../../../backend/src/main/java/com/planted/bio/BioSectionContext.java) (resolved from `SPECIES_ID` section) |
| `location` | `Plant.getLocation()` — placement notes as context for the placement cause branch; owner-asserted placement is authoritative (OWNER CERTAINTY). |
| `goals_text` | `BioSectionContext.goalsText` → `Plant.getGoalsText()` — owner-asserted working care practices are authoritative (OWNER CERTAINTY). |
| `notes_text` | `BioSectionContext.notesText` — plain journal notes text from [`OwnerNoteFormatter`](../../../backend/src/main/java/com/planted/service/OwnerNoteFormatter.java). Owner-asserted facts and recent care observations are authoritative. |

## Output schema

- `response_format` name: `plant_health_assessment_response`.
- Schema: [`BioSectionSchemas.healthAssessment`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{
  diagnosis: string,             // 2–5 sentences
  severity: "NONE"|"MILD"|"MODERATE"|"SEVERE"|"UNCERTAIN",
  signs: string[],               // up to 5 short visible-sign phrases
  checks: string[],              // 1–3 owner-checkable prompts
  attentionNeeded: boolean,      // true for MILD/MODERATE/SEVERE
  attentionReason: string        // short tooltip when attentionNeeded is true
}
```

## System prompt (active version 6)

```
You assess the visible condition of a single houseplant from a photo. Do NOT default to "healthy". Actively scan the image for chlorosis, necrosis, crispy edges, wilting, leaf drop, curling, leggy/etiolated growth, pale color, sunburn, mechanical damage, pests or webbing, powdery or sooty coatings, mold, soil crust, rot at base, and pot/substrate issues.

For every finding, consider whichever of these three stressors fit: (1) LIGHT — too little (pale, leggy, small new growth) or too much / wrong quality (bleached patches, sunburn). When the photo shows visible light-stress symptoms (etiolation, stretching toward light, sunburn, bleaching) that are atypical for the identified species' light preference, you MUST call this out by name in diagnosis and list the concrete sign in signs; do not bury the light cause under a generic "may need adjustment". (2) WATERING with particular attention to OVERWATERING (uniformly yellow lower leaves, soft or mushy stems, persistently wet soil, fungus gnats, wilting despite wet soil, rot). (3) PLACEMENT (heat vents, AC, cold drafts, leaf contact with cold glass, low humidity, no airflow, saucer with standing water, hidden behind a curtain) AND POT SIZING when the plant is visibly potted: pot too small for the plant, roots escaping drainage holes, soil surface crowded with roots, plant top-heavy or unstable relative to its pot, pot cracked or bulging, or any other sign the plant has outgrown its container. Offer 1–3 plausible causes per finding; do not lock to a single cause.

OWNER CERTAINTY — the owner's placement notes, goals, and journal notes are a tiered source of evidence:
1. ASSERTIONS — statements of fact about where the plant lives ("south-facing window", "bathroom with a skylight", "outdoor covered porch") or about a working care practice ("I water weekly and it's thriving", "I repotted last spring"). Treat these as AUTHORITATIVE TRUTH about placement and care history; use them to inform the placement / light / watering cause branches without second-guessing where the plant actually is or what care is actually happening. Do not diagnose an overwatering or underwatering cause against an asserted working routine without a clear visible sign in the photo.
2. HEDGED HINTS — uncertain phrasings ("I think it gets some morning sun", "maybe a little drafty", "not sure if I've been watering enough"). Treat these as SOFT EVIDENCE that may be revised by the image.

Only call the plant healthy when there are no visible concerns AND the image is clear enough to judge. If the photo is blurry, cropped, or poorly lit, say the image is insufficient rather than declaring health. If the plant is clearly not in a pot (in-ground, landscape, or no container visible), do not speculate about pot sizing or repotting. Tone: honest and specific, not alarmist, beginner-friendly. Do not produce a full care plan — schedules belong in the dedicated care sections.

ATTENTION FLAG: Set attentionNeeded to true whenever severity is MILD, MODERATE, or SEVERE. Set attentionNeeded to false when severity is NONE or UNCERTAIN. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) summarising the most actionable health concern; otherwise return an empty string.
```

## User template (active version 6)

```
Assess the visible condition of this plant.
{{#if species_name}}Species (already identified; use as context only, do not re-ID): {{species_name}}{{/if}}
{{#if location}}Owner's placement notes (apply OWNER CERTAINTY — owner-asserted facts about where the plant lives are authoritative): {{location}}{{/if}}
{{#if goals_text}}Owner goals and any asserted care practices (apply OWNER CERTAINTY — asserted working routines are authoritative; do not contradict them without a clear visible sign): {{goals_text}}{{/if}}
{{#if notes_text}}Owner journal notes (newest first; apply OWNER CERTAINTY — asserted facts and recent care observations here are authoritative):
{{notes_text}}{{/if}}

Return JSON with:
- diagnosis: 2–5 sentences of realistic assessment as described in the system prompt.
- severity: one of NONE, MILD, MODERATE, SEVERE, UNCERTAIN. Use UNCERTAIN when the image is insufficient.
- signs: up to 5 short concrete visible signs (e.g. "crispy tips on lower leaves"). When the plant is visibly potted and the pot looks too small or outgrown, include a concrete pot-sizing sign (e.g. "roots visible at drainage holes" or "plant top-heavy for its pot"). Empty array if none.
- checks: 1–3 short owner-checkable prompts that target whichever of light, watering, placement, or pot sizing is most plausible (e.g. "lift the pot — do roots circle the soil ball?"). Empty array if the plant is clearly healthy. Skip pot-sizing entries entirely when the plant is not in a pot.
- attentionNeeded: true when severity is MILD/MODERATE/SEVERE; false for NONE/UNCERTAIN.
- attentionReason: short tooltip phrase (under 80 chars) summarising the health concern when attentionNeeded is true. Empty string otherwise.
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed. |
| [V40](../../../backend/src/main/resources/db/migration/V40__bio_prompts_v2_repotting.sql) | Promote pot-sizing to an explicit scanned finding; add `signs` and `checks` guidance for pot-outgrown plants; ignore for plants not in a pot. |
| [V42](../../../backend/src/main/resources/db/migration/V42__bio_prompts_v3_attention_flags.sql) | Add `attentionNeeded` + `attentionReason` so the health icon in the plant list + screensaver can illuminate for MILD/MODERATE/SEVERE cases. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule — owner-asserted placement and working care practices are authoritative, not soft context. |
| [V45](../../../backend/src/main/resources/db/migration/V45__bio_prompts_v4_vision_light_placement.sql) | Reinforce LIGHT cause branch — when the photo shows visible light-stress symptoms atypical for the species, the diagnosis must name the light cause and list the concrete sign rather than burying it under a generic "may need adjustment". Paired with [V46](../../../backend/src/main/resources/db/migration/V46__invalidate_vision_light_placement_sections.sql) which backfills cached rows. |
| [V47](../../../backend/src/main/resources/db/migration/V47__owner_notes_bio_inputs.sql) | Thread `goals_text` + `notes_text` into the prompt so owner-asserted working care routines and journal-note observations can inform cause branches (and override photo-driven overwatering / underwatering conclusions when there is no clear visible sign). Preserves the V45 VISUAL CHECK for light-stress symptoms. |
