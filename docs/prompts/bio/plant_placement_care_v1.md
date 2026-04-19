# plant_placement_care_v1

> Bio section: short placement recommendation tailored to the owner's stated notes + geography, plus general room-environment tips. Text-only. Also emits an `attentionNeeded` boolean that drives the placement icon on the plant list + screensaver icon row. Replaces [`plant_placement_notes_summary_v1`](../placement/plant_placement_notes_summary_v1.md) once fully rolled out.

## Summary

- **Asks for:** recommend a specific placement for THIS plant given the owner's stated location, and write general room-environment tips (drafts, humidity, grouping, rotation) without repeating the tailored recommendation. Decide whether the plant should be MOVED or REPOTTED.
- **Returns:** `{ guidance, generalGuidance, attentionNeeded, attentionReason }`. `attentionNeeded` is conservative (default false); `attentionReason` is a short tooltip-length phrase when true, empty string otherwise.

## Used by

- [`PlacementCareStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/PlacementCareStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) → [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- `attentionNeeded` + `attentionReason` read by [`PlantReminderService.syncBioAttention`](../../../backend/src/main/java/com/planted/service/PlantReminderService.java) and persisted onto `plant_reminder_state.placement_attention_needed` / `placement_attention_reason`.

## Input variables

| Variable | Required | Source |
|---|---|---|
| `species_name` | yes | [`BioSectionContext.speciesName`](../../../backend/src/main/java/com/planted/bio/BioSectionContext.java) |
| `growing_context` | no | `Plant.getGrowingContext().name()` |
| `location` | no | `Plant.getLocation()` |
| `geographic_location` | no | `BioSectionContext.geographicLocation` |

## Output schema

- `response_format` name: `plant_placement_care_response`.
- Schema: [`BioSectionSchemas.placementCare`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{ guidance: string, generalGuidance: string, attentionNeeded: boolean, attentionReason: string }
```

## System prompt (active version 4)

```
You produce short, practical placement guidance for a single plant: where it should live, what environments to avoid, and general room-environment tips. Tailor to the owner's stated location when provided.

OWNER CERTAINTY — the owner's placement notes are a tiered source of evidence:
1. ASSERTIONS — factual-sounding claims about where the plant actually lives ("south-facing window, 2ft back", "west-facing balcony", "bathroom with a skylight"). Treat these as AUTHORITATIVE TRUTH about the plant's current placement. Tailor guidance around that placement rather than recommending a different one, unless an ASSERTED placement is a clear and immediate threat (e.g. direct contact with a cold single-pane window in winter, saucer sitting in standing water). Even then, frame the recommendation as an adjustment to the asserted placement, not as if the owner hadn't told you.
2. HEDGED HINTS — uncertain phrasings ("I think it gets some morning sun", "maybe a little drafty"). Treat these as SOFT EVIDENCE you may cross-check against the species.

POT-SIZING / REPOTTING: If the plant is likely in a pot — inferable from an INDOOR growing context, owner notes mentioning a pot / planter / container / nursery pot, or species that are almost always grown in containers — briefly consider whether it may be outgrowing its pot or the pot is too small. Flag conservatively using observable signs: roots escaping drainage holes, roots circling the soil ball, top-heavy or unstable plant relative to its pot, soil drying out unusually fast between waterings, a visibly small nursery pot the plant was never moved out of. Do not prescribe a full repotting schedule; one short sentence is enough. If the plant is clearly NOT in a pot (in-ground, garden bed, landscape, or an OUTDOOR growing context with no container hints), omit pot and repotting discussion entirely — do not speculate.

ATTENTION FLAG: Set attentionNeeded to true when the plant should be MOVED (drafty/overheated spot, wrong humidity, leaves pressed against cold glass, saucer sitting in water, etc.) OR REPOTTED (concrete signs from the pot-sizing rules above). Be conservative: when the owner's placement notes and growing_context look fine for the species, or when you are unsure, set attentionNeeded to false. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) the owner can act on; otherwise return an empty string.
```

## User template (active version 4)

```
Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Owner's placement notes (apply OWNER CERTAINTY — asserted placement facts describe where the plant actually is; tailor guidance around that placement): {{location}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}

Return JSON with:
- guidance: 1–2 short sentences recommending a specific placement for THIS plant (tailored to the owner's notes when present; when the notes assert where the plant currently lives, reinforce that placement rather than recommending a different one unless it is a clear threat).
- generalGuidance: 2–3 sentences of general room-environment tips for this species (drafts, humidity, grouping, rotation). Do not repeat the exact wording of guidance.
- attentionNeeded: true only if the plant likely needs to be moved or repotted based on the owner's notes or growing_context. Default to false when uncertain or when notes do not describe a problem.
- attentionReason: short tooltip phrase (under 80 chars) when attentionNeeded is true, e.g. "Likely outgrowing its pot" or "Drafty spot near a vent". Empty string when attentionNeeded is false.

If the plant is likely potted, include one short repotting check within guidance or generalGuidance (whether it may be outgrowing its pot or the pot is too small, with a single observable sign the owner can watch for). Omit this entirely if the plant is in-ground or not in a pot.
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed. |
| [V40](../../../backend/src/main/resources/db/migration/V40__bio_prompts_v2_repotting.sql) | Add pot-sizing / repotting awareness for potted plants; ignore for in-ground plants. |
| [V42](../../../backend/src/main/resources/db/migration/V42__bio_prompts_v3_attention_flags.sql) | Add `attentionNeeded` + `attentionReason` so the placement icon in the plant list + screensaver can illuminate when the plant should be moved or repotted. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule — asserted placement facts describe where the plant actually lives and must not be overridden by a generic placement recommendation. |
