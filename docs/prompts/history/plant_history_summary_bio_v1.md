# plant_history_summary_bio_v1

> Bio section: a compact one-paragraph history summary drawn strictly from the plant's timeline facts. Distinct from [`plant_history_summary_v2`](plant_history_summary_v2.md) (which produces per-day digests for the History page).

## Summary

- **Asks for:** given the plant's timeline text (journal notes, waterings, fertilizings, prunings) and optional species name, write one short paragraph describing what has happened with this plant so far. Conservative tone. Neutral past tense. No advice. No invented events.
- **Returns:** `{ "summary": string }` — one short paragraph (3–6 sentences), or an empty string when the timeline is empty.

## Used by

- [`HistorySummaryStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/HistorySummaryStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) line 142, calling [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- Result is cached in `plant_bio_sections` keyed by `(plant_id, HISTORY_SUMMARY)` and rendered in the plant bio panel.

## Input variables

| Variable | Required | Source |
|---|---|---|
| `species_name` | no | [`BioSectionContext.speciesName`](../../../backend/src/main/java/com/planted/bio/BioSectionContext.java) |
| `timeline_text` | yes | `BioSectionContext.timelineText` (built from journal + care events for this plant) |

No image attached. Text-only.

## Output schema

- `response_format` name: `plant_history_summary_bio_response`.
- Schema builder: [`BioSectionSchemas.historySummary`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{ summary: string }  // 3–6 sentences; empty string if timeline_text is empty
```

## System prompt (active version 2)

```
You write a short single-paragraph history summary for a plant's bio, drawing only on the timeline facts provided (journal notes, waterings, fertilizings, prunings). Do not invent events. Conservative tone. No bullets; one paragraph. Neutral past-tense observational style; do not offer advice.

OWNER CERTAINTY — when the timeline text contains owner-asserted facts about the plant (species, cultivar, origin, native range, placement, a care routine that is working), treat those assertions as AUTHORITATIVE TRUTH and reflect them in the summary. Do not soften, hedge, or contradict an asserted fact. Only hedged owner phrasings ("I think", "maybe", "not sure") should themselves be reported with hedging.
```

## User template (active version 2)

```
{{#if species_name}}Species: {{species_name}}
{{/if}}Timeline facts (newest first):
{{timeline_text}}

Return JSON with:
- summary: one short paragraph (3–6 sentences) describing what has happened with this plant so far. Honor owner-asserted facts in the timeline as authoritative per OWNER CERTAINTY. If the timeline is empty, return an empty string.
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed alongside the other bio-section prompts. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule — owner-asserted facts in journal notes are authoritative in the bio history paragraph. |

## Notes

- The `timeline_text` handed to this prompt is simpler than the one for `plant_history_summary_v2` — no images, no care-count grouping — because the bio card only needs a conservative paragraph, not a multi-day narrative.
