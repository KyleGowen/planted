# plant_fertilizer_care_v1

> Bio section: short, practical fertilizer guidance. Conservative, beginner-friendly. Text-only.

## Summary

- **Asks for:** recommend a conservative fertilizer regimen (balanced, diluted, infrequent in winter). Do not recommend specialized regimens unless the species is well-characterized.
- **Returns:** `{ type, frequency, guidance }` — short phrases for the first two, 2–4 sentences on how to feed without burning the plant and when to back off.

## Used by

- [`FertilizerCareStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/FertilizerCareStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) → [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).

## Input variables

| Variable | Required | Source |
|---|---|---|
| `species_name` | yes | [`BioSectionContext.speciesName`](../../../backend/src/main/java/com/planted/bio/BioSectionContext.java) |
| `growing_context` | no | `Plant.getGrowingContext().name()` |
| `geographic_location` | no | `BioSectionContext.geographicLocation` |
| `goals_text` | no | `BioSectionContext.goalsText` → `Plant.getGoalsText()` — owner-asserted feed routines / bloom or growth goals are authoritative (OWNER CERTAINTY). |
| `notes_text` | no | `BioSectionContext.notesText` — plain journal notes text from [`OwnerNoteFormatter`](../../../backend/src/main/java/com/planted/service/OwnerNoteFormatter.java). Owner-asserted feeding cadence or plant-behaviour observations are authoritative. |

No placement notes — fertilizer guidance doesn't need them.

## Output schema

- `response_format` name: `plant_fertilizer_care_response`.
- Schema: [`BioSectionSchemas.fertilizerCare`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{ type: string, frequency: string, guidance: string }
```

## System prompt (active version 3)

```
You produce short, practical fertilizer guidance for a single plant. Prefer conservative recommendations for beginners (balanced, diluted, infrequent in winter). Do not recommend specialized regimens unless the species is well-characterized.

OWNER CERTAINTY — when the owner's notes (goals_text, notes_text) assert a fact about their current feeding routine or goals ("I feed monthly with a balanced 10-10-10 and it's thriving", "I don't fertilize at all", "I want it to bloom"), treat those assertions as AUTHORITATIVE TRUTH. Tailor type / frequency / guidance to reinforce a working asserted routine rather than substituting a generic one. Only hedged phrasings ("I think", "maybe", "the store said") should be treated as soft evidence.
```

## User template (active version 3)

```
Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}
{{#if goals_text}}Owner notes and any claims about feeding routine or goals (apply OWNER CERTAINTY — owner-asserted facts are authoritative): {{goals_text}}{{/if}}
{{#if notes_text}}Owner journal notes (newest first; apply OWNER CERTAINTY — asserted feeding cadence or plant-behaviour observations are authoritative):
{{notes_text}}{{/if}}

Return JSON with:
- type: short phrase (e.g. "Balanced liquid fertilizer, diluted to half strength"). If the owner has asserted a working type, reflect it.
- frequency: short phrase (e.g. "Every 4–6 weeks during active growth; pause in winter"). If the owner has asserted a working cadence, reflect it.
- guidance: 2–4 sentences describing how to feed this plant without burning it, and when to back off. Fold in any owner-asserted routine rather than contradicting it.
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule and thread `goals_text` through so owner-asserted feeding routines and bloom goals are honored. |
| [V47](../../../backend/src/main/resources/db/migration/V47__owner_notes_bio_inputs.sql) | Also thread `notes_text` through so owner-asserted feeding cadence or observations in journal notes are honored alongside `goals_text`. |
