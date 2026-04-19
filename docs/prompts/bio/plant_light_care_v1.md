# plant_light_care_v1

> Bio section: short, practical light guidance for one plant. Text-only. Also emits an `attentionNeeded` boolean that drives the light icon on the plant list + screensaver icon row.

## Summary

- **Asks for:** describe the brightness level typical for the species and how owners can recognize too-little or too-much light. Decide whether the plant is LIKELY receiving too much or too little light given the owner's growing_context + location.
- **Returns:** `{ needs, generalGuidance, attentionNeeded, attentionReason }` — `needs` is one short primary line for the UI; `generalGuidance` is 2–3 sentences of secondary educational text (window distance, seasonal changes, signs of too much/little light) without repeating `needs`; `attentionNeeded` is conservative (default false); `attentionReason` is a short tooltip-length phrase when `attentionNeeded` is true and an empty string otherwise.

## Used by

- [`LightCareStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/LightCareStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) → [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- `attentionNeeded` + `attentionReason` read by [`PlantReminderService.syncBioAttention`](../../../backend/src/main/java/com/planted/service/PlantReminderService.java) and persisted onto `plant_reminder_state.light_attention_needed` / `light_attention_reason` so the list + screensaver can illuminate the sun icon without re-reading bio content.

## Input variables

| Variable | Required | Source |
|---|---|---|
| `species_name` | yes | [`BioSectionContext.speciesName`](../../../backend/src/main/java/com/planted/bio/BioSectionContext.java) |
| `growing_context` | no | `Plant.getGrowingContext().name()` |
| `location` | no | `Plant.getLocation()` |

## Output schema

- `response_format` name: `plant_light_care_response`.
- Schema: [`BioSectionSchemas.lightCare`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{ needs: string, generalGuidance: string, attentionNeeded: boolean, attentionReason: string }
```

## System prompt (active version 3)

```
You produce short, practical light guidance for a single plant. Describe the brightness level typical for the species and how owners can recognize too-little or too-much light.

OWNER CERTAINTY — the owner's placement notes are a tiered source of evidence:
1. ASSERTIONS — factual claims about light or placement ("south-facing window", "bright indirect near an east window", "gets 4 hours of direct afternoon sun"). Treat these as AUTHORITATIVE TRUTH about the light this plant is actually receiving. Reason about mismatch (attentionNeeded) against the asserted light, not against a generic "we don't know where it lives" baseline.
2. HEDGED HINTS — uncertain phrasings ("I think it gets some morning sun", "maybe enough light"). Treat these as SOFT EVIDENCE.

ATTENTION FLAG: Based on the species' typical light preference and the owner's stated growing_context + location, decide whether the plant is LIKELY receiving too much or too little light right now. Set attentionNeeded to true only when there is a concrete mismatch (e.g. a low-light species described as living in "south window, full sun all afternoon", or a full-sun species described as living in a dim bathroom / far from windows / "north-facing corner"). Be conservative — when the owner has not described their light conditions, or the description is compatible with the species' needs, or you are unsure, set attentionNeeded to false. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) the owner can act on; otherwise return an empty string.
```

## User template (active version 3)

```
Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Placement notes (apply OWNER CERTAINTY — asserted facts about the plant's light are authoritative): {{location}}{{/if}}

Return JSON with:
- needs: short primary line for the UI (e.g. "Bright indirect light"). One phrase or one short sentence.
- generalGuidance: 2–3 sentences of secondary educational text — what the label means, typical distance from windows, seasonal changes, conservative signs of too much or too little light. Do not repeat the exact wording of needs.
- attentionNeeded: true only if the species' light needs clearly mismatch the owner-described placement (too much OR too little light). Default to false when uncertain or when placement notes are missing.
- attentionReason: short tooltip phrase (under 80 chars) when attentionNeeded is true, e.g. "Likely too dark for this species" or "Harsh afternoon sun may scorch leaves". Empty string when attentionNeeded is false.
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed. |
| [V42](../../../backend/src/main/resources/db/migration/V42__bio_prompts_v3_attention_flags.sql) | Add `attentionNeeded` + `attentionReason` so the light icon in the plant list + screensaver can illuminate when owner-described placement implies a light mismatch. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule — asserted facts about the plant's light are authoritative and drive the attention flag directly. |
