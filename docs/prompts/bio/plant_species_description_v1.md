# plant_species_description_v1

> Bio section: short encyclopedia-style prose about the species in the voice of a Wikipedia lead. Third person. No bullets. Text-only (no image).

## Summary

- **Asks for:** write a neutral encyclopedia-style description of the given species: taxonomy, morphology, native range / ecology, horticultural role, brief indoor context. Do not duplicate structured care schedules. State uncertainty clearly if ID is weak.
- **Returns:** `{ overview: string (1–3 paragraphs, separated by blank lines), uses: string[] (up to 5) }`.

## Used by

- [`SpeciesDescriptionStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/SpeciesDescriptionStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) → [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- Text-only: no image attached.

## Input variables

| Variable | Required | Source |
|---|---|---|
| `species_name` | yes | [`BioSectionContext.speciesName`](../../../backend/src/main/java/com/planted/bio/BioSectionContext.java) |
| `taxonomic_family` | no | `BioSectionContext.taxonomicFamily` |
| `native_regions` | no | `BioSectionContext.nativeRegions` (comma-joined list) |
| `goals_text` | no | `BioSectionContext.goalsText` → `Plant.getGoalsText()` — owner-asserted facts about origin / native range / cultivar are authoritative (OWNER CERTAINTY). |
| `notes_text` | no | `BioSectionContext.notesText` — plain journal notes text from [`OwnerNoteFormatter`](../../../backend/src/main/java/com/planted/service/OwnerNoteFormatter.java) (newest first). Owner-asserted origin / cultivar / native-range claims here are authoritative. |

## Output schema

- `response_format` name: `plant_species_description_response`.
- Schema: [`BioSectionSchemas.speciesDescription`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{
  overview: string,  // 1–3 paragraphs separated by two newline characters
  uses: string[]     // up to 5 short uses (culinary, ornamental, medicinal only if non-fringe)
}
```

## System prompt (active version 3)

```
You write short neutral encyclopedia-style prose about plant species, in the voice of a Wikipedia article lead. Third person only, no bullets or numbered lists. Cover taxonomy, morphology, native range/ecology at high level, horticultural role, and brief indoor context. Avoid duplicating structured care schedules — those live in dedicated care sections. State uncertainty clearly if the identification is weak.

OWNER CERTAINTY — when the owner's notes (goals_text, notes_text) assert a fact about this individual plant — species, cultivar, native range, origin, where it was collected, inherited, or propagated from — treat that assertion as AUTHORITATIVE TRUTH and reflect it in the overview. If the owner has asserted a native range that differs from what you would otherwise write about the species, follow the owner's assertion. Only hedged phrasings ("I think", "maybe", "labeled as", "might be", "the store said") should be treated as soft evidence. Do not contradict asserted owner facts with generic species knowledge.
```

## User template (active version 3)

```
Write an encyclopedia-style description for the following species.

Species: {{species_name}}
{{#if taxonomic_family}}Family: {{taxonomic_family}}{{/if}}
{{#if native_regions}}Native regions: {{native_regions}}{{/if}}
{{#if goals_text}}Owner notes and any claims about origin or identity (apply OWNER CERTAINTY — owner-asserted facts here are authoritative and must be honored in the overview): {{goals_text}}{{/if}}
{{#if notes_text}}Owner journal notes (newest first; apply OWNER CERTAINTY — asserted facts are authoritative):
{{notes_text}}{{/if}}

Return JSON with:
- overview: exactly 1–3 paragraphs, each multiple full sentences, separated by two newline characters.
- uses: up to 5 short uses where clearly safe and relevant (culinary, ornamental, medicinal only if non-fringe). Empty array if none apply.
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule and thread `goals_text` through so owner-asserted origin / native-range / cultivar claims reach the overview. |
| [V47](../../../backend/src/main/resources/db/migration/V47__owner_notes_bio_inputs.sql) | Also thread `notes_text` through so owner-asserted origin / cultivar / native-range claims in journal notes reach the overview alongside `goals_text`. |
