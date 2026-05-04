# plant_light_care_v1

> Bio section: short, practical light guidance for one plant. **Vision call** — the plant's primary photo is attached automatically so the prompt can scan for symptoms of light mismatch against the species' needs. Also emits an `attentionNeeded` boolean that drives the light icon on the plant list + screensaver icon row.

## Summary

- **Asks for:** describe the brightness level typical for the species and how owners can recognize too-little or too-much light. Scan the attached photo for visible light-stress symptoms (etiolation, stretching, sunburn, bleaching, pale weak growth) and decide whether the plant is LIKELY receiving too much or too little light given BOTH the owner's growing_context + location AND what the photo shows.
- **Returns:** `{ needs, generalGuidance, attentionNeeded, attentionReason }` — `needs` is one short primary line for the UI; `generalGuidance` is 2–3 sentences of secondary educational text (window distance, seasonal changes, signs of too much/little light) without repeating `needs`; `attentionNeeded` is conservative (default false); `attentionReason` is a short tooltip-length phrase when `attentionNeeded` is true (naming the visible symptom when applicable) and an empty string otherwise.

## Used by

- [`LightCareStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/LightCareStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) → [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- Uses the plant's primary image (vision call); `PlantBioSectionKey.LIGHT_CARE` has `requiresImage=true` so the processor attaches the latest `ORIGINAL_UPLOAD` automatically.
- `attentionNeeded` + `attentionReason` read by [`PlantReminderService.syncBioAttention`](../../../backend/src/main/java/com/planted/service/PlantReminderService.java) and persisted onto `plant_reminder_state.light_attention_needed` / `light_attention_reason` so the list + screensaver can illuminate the sun icon without re-reading bio content.

## Input variables

| Variable | Required | Source |
|---|---|---|
| `species_name` | yes | [`BioSectionContext.speciesName`](../../../backend/src/main/java/com/planted/bio/BioSectionContext.java) |
| `growing_context` | no | `Plant.getGrowingContext().name()` |
| `location` | no | `Plant.getLocation()` — owner-asserted placement is authoritative (OWNER CERTAINTY). |
| `goals_text` | no | `BioSectionContext.goalsText` → `Plant.getGoalsText()` — owner-asserted light / placement facts are authoritative (OWNER CERTAINTY). |
| `notes_text` | no | `BioSectionContext.notesText` — plain journal notes text from [`OwnerNoteFormatter`](../../../backend/src/main/java/com/planted/service/OwnerNoteFormatter.java). Owner-asserted light / placement facts in journal notes are authoritative. |

In addition to the template variables above, the plant's primary image (`ORIGINAL_UPLOAD`, selected by [`PlantBioSectionProcessor.loadPrimaryImageAsBase64`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java)) is attached to the user message as a vision input.

## Output schema

- `response_format` name: `plant_light_care_response`.
- Schema: [`BioSectionSchemas.lightCare`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{ needs: string, generalGuidance: string, attentionNeeded: boolean, attentionReason: string }
```

## System prompt (active version 5)

```
You produce short, practical light guidance for a single plant. Describe the brightness level typical for the species and how owners can recognize too-little or too-much light.

OWNER CERTAINTY — the owner's placement notes, goals, and journal notes are a tiered source of evidence:
1. ASSERTIONS — factual claims about light or placement ("south-facing window", "bright indirect near an east window", "gets 4 hours of direct afternoon sun"). Treat these as AUTHORITATIVE TRUTH about the light this plant is actually receiving. Reason about mismatch (attentionNeeded) against the asserted light, not against a generic "we don't know where it lives" baseline.
2. HEDGED HINTS — uncertain phrasings ("I think it gets some morning sun", "maybe enough light"). Treat these as SOFT EVIDENCE.

VISUAL CHECK — the user message includes the plant's primary photo. Scan it specifically for symptoms of light mismatch against this species' needs:
- TOO LITTLE LIGHT: leggy/etiolated stems with widely-spaced leaves, stems reaching one direction, pale or washed-out color, small or slow new growth, loss of variegation or vibrant color on species that depend on it.
- TOO MUCH LIGHT: bleached patches on the sun-facing side, crispy sunburn, yellowing or reddening that matches hot-sun exposure, scorched leaf tips on shade-loving species.
The photo is authoritative about what the plant currently looks like; visible symptoms outrank silence in placement notes. If the photo is blurry, cropped, or insufficient, do not invent symptoms — fall back to asserted evidence only.

ATTENTION FLAG: Set attentionNeeded to true when EITHER (a) there is a concrete mismatch between the species' light preference and the owner's asserted placement (e.g. a low-light species described as living in "south window, full sun all afternoon", or a full-sun species described as living in a dim bathroom / far from windows / "north-facing corner"), OR (b) the photo shows concrete visible symptoms of light stress (etiolation, stretching, sunburn, bleaching, pale weak growth) that are atypical for the identified species' light preference. Visible symptoms are a first-class trigger — if the photo shows a full-sun species visibly stretching, set attentionNeeded to true even when placement notes are missing. Be conservative about ambiguous cases: when the description is compatible with the species' needs and the photo shows no clear symptoms, or you are unsure, set attentionNeeded to false. When attentionNeeded is true, attentionReason must be a short tooltip-length phrase (under 80 chars) the owner can act on, naming the visible symptom when applicable (e.g. "Stretched, leggy growth suggests not enough light" or "Bleached patches suggest too much direct sun"); otherwise return an empty string.
```

## User template (active version 5)

```
Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Placement notes (apply OWNER CERTAINTY — asserted facts about the plant's light are authoritative): {{location}}{{/if}}
{{#if goals_text}}Owner goals and any claims about light or placement (apply OWNER CERTAINTY — asserted facts are authoritative): {{goals_text}}{{/if}}
{{#if notes_text}}Owner journal notes (newest first; apply OWNER CERTAINTY — asserted light / placement facts are authoritative):
{{notes_text}}{{/if}}

The attached photo is the plant's current primary image — apply the VISUAL CHECK rule from the system instructions and let visible symptoms of light mismatch drive attentionNeeded even when placement notes are silent.

Return JSON with:
- needs: short primary line for the UI (e.g. "Bright indirect light"). One phrase or one short sentence.
- generalGuidance: 2–3 sentences of secondary educational text — what the label means, typical distance from windows, seasonal changes, conservative signs of too much or too little light. Do not repeat the exact wording of needs.
- attentionNeeded: true when either the species' light needs clearly mismatch the owner-described placement OR the photo shows visible light-stress symptoms (etiolation, stretching, sunburn, bleaching) atypical for this species. Default to false when uncertain, when placement looks fine, and the photo shows no clear symptoms.
- attentionReason: short tooltip phrase (under 80 chars) when attentionNeeded is true, naming the visible symptom when applicable, e.g. "Stretched, leggy growth suggests not enough light" or "Harsh afternoon sun may scorch leaves". Empty string when attentionNeeded is false.
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed. |
| [V42](../../../backend/src/main/resources/db/migration/V42__bio_prompts_v3_attention_flags.sql) | Add `attentionNeeded` + `attentionReason` so the light icon in the plant list + screensaver can illuminate when owner-described placement implies a light mismatch. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule — asserted facts about the plant's light are authoritative and drive the attention flag directly. |
| [V45](../../../backend/src/main/resources/db/migration/V45__bio_prompts_v4_vision_light_placement.sql) | Promote to a vision call. Add VISUAL CHECK block; visible symptoms of light stress (etiolation, stretching, sunburn, bleaching) now flip `attentionNeeded` even when placement notes are silent. Paired with `PlantBioSectionKey.LIGHT_CARE.requiresImage=true` so the primary image is attached automatically, and [V46](../../../backend/src/main/resources/db/migration/V46__invalidate_vision_light_placement_sections.sql) which backfills cached rows. |
| [V47](../../../backend/src/main/resources/db/migration/V47__owner_notes_bio_inputs.sql) | Thread `goals_text` + `notes_text` into the prompt so owner-asserted light / placement facts in goals or journal notes flow in alongside `location`. Preserves the V45 VISUAL CHECK. |
