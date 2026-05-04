# plant_pruning_care_v1

> Bio section: short, practical species-level pruning guidance. "No pruning required" is a valid and often preferred answer. Text-only.

## Summary

- **Asks for:** write WHEN (best season or triggers) and HOW MUCH to prune for this species. Include a short `actionSummary`, a one- or two-sentence `guidance`, and a separate `generalGuidance` describing species-typical pruning habits.
- **Returns:** `{ actionSummary, guidance, generalGuidance }`.

Distinct from [`pruning_analysis_v1`](../pruning/pruning_analysis_v1.md), which is the **per-photo** pruning advisor that uses 1–3 images. This one is species-level, text-only, and cheaply regeneratable.

## Used by

- [`PruningCareStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/PruningCareStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) → [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).

## Input variables

| Variable | Required | Source |
|---|---|---|
| `species_name` | yes | [`BioSectionContext.speciesName`](../../../backend/src/main/java/com/planted/bio/BioSectionContext.java) |
| `growing_context` | no | `Plant.getGrowingContext().name()` |
| `geographic_location` | no | `BioSectionContext.geographicLocation` |
| `goals_text` | no | `BioSectionContext.goalsText` → `Plant.getGoalsText()` — owner-asserted pruning routines / shaping goals are authoritative (OWNER CERTAINTY). |
| `notes_text` | no | `BioSectionContext.notesText` — plain journal notes text from [`OwnerNoteFormatter`](../../../backend/src/main/java/com/planted/service/OwnerNoteFormatter.java). Owner-asserted pruning routines or recent pruning events are authoritative. |

## Output schema

- `response_format` name: `plant_pruning_care_response`.
- Schema: [`BioSectionSchemas.pruningCare`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{ actionSummary: string, guidance: string, generalGuidance: string }
```

## System prompt (active version 3)

```
You produce short, practical pruning guidance for a single plant. "No pruning required" is a valid and often preferred answer. Include WHEN to prune (best season or triggers) and HOW MUCH to remove (conservative). Do not recommend aggressive pruning.

OWNER CERTAINTY — when the owner's notes (goals_text, notes_text) assert a fact about their pruning routine or shaping goal ("I pinch tips every spring and it bushes out", "I want to keep it compact", "I shape it heavily once a year", "pruned hard last month"), treat that as AUTHORITATIVE TRUTH. Tailor actionSummary / guidance to reinforce a working asserted routine rather than overriding it with a generic conservative default. Only hedged phrasings ("I think", "maybe", "might try") should be treated as soft evidence.
```

## User template (active version 3)

```
Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}
{{#if goals_text}}Owner notes and any claims about pruning routine or shaping goals (apply OWNER CERTAINTY — owner-asserted facts are authoritative): {{goals_text}}{{/if}}
{{#if notes_text}}Owner journal notes (newest first; apply OWNER CERTAINTY — asserted pruning routines or recent pruning events are authoritative):
{{notes_text}}{{/if}}

Return JSON with:
- actionSummary: 2–3 sentences covering WHEN (season or visible triggers) and HOW MUCH to prune for this species. "No pruning required" is valid. If the owner has asserted a working routine, reflect it.
- guidance: 1–2 sentences of general pruning advice for this species.
- generalGuidance: 1–2 sentences of species-typical pruning habits and rationale (do not repeat actionSummary verbatim).
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule and thread `goals_text` through so owner-asserted pruning cadence and shaping goals are honored. |
| [V47](../../../backend/src/main/resources/db/migration/V47__owner_notes_bio_inputs.sql) | Also thread `notes_text` through so owner-asserted pruning routines or recent pruning events in journal notes are honored alongside `goals_text`. |
