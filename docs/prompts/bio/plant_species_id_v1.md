# plant_species_id_v1

> Bio section: conservative plant identification from a single photo. Used by the decomposed bio pipeline instead of the monolithic registration prompt when we only need ID.

## Summary

- **Asks for:** identify the plant in the attached image. Never hallucinate species; when unsure, leave fields blank and explain the uncertainty in `confidence`. `species` must be the specific epithet only (not the full binomial).
- **Returns:** `{ className, taxonomicFamily, genus, species, variety, confidence, nativeRegions[] }`.

## Used by

- [`SpeciesIdStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/SpeciesIdStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) → [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- Result cached in `plant_bio_sections` under key `SPECIES_ID` and used to feed the `species_name` context for the text-only care bio sections.

## Input variables

All optional.

| Variable | Source |
|---|---|
| `plant_name` | `Plant.getName()` — owner-asserted names are authoritative (see OWNER CERTAINTY rule); hedged phrasings stay soft evidence. |
| `location` | `Plant.getLocation()` — owner-asserted placement is authoritative; hedged phrasings stay soft evidence. |

Image is required at the transport level: the strategy opts into vision (`key().requiresImage()` is true for `SPECIES_ID`), so [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) loads the plant's primary image as base64 and attaches it.

## Output schema

- `response_format` name: `plant_species_id_response`.
- Schema: [`BioSectionSchemas.speciesId`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{ className, taxonomicFamily, genus, species /* epithet only */, variety, confidence, nativeRegions: string[] }
```

## System prompt (active version 2)

```
You are a conservative plant identification expert. Look at the plant image and return a structured JSON identification only. Never hallucinate a species from the image alone; when the image is unclear and the owner has said nothing, leave fields blank and explain the uncertainty in "confidence". State the specific epithet (second word of the binomial) in "species", not the full binomial.

OWNER CERTAINTY — the owner's own statements (plant_name, location) are a tiered source of evidence:
1. ASSERTIONS — factual-sounding claims the owner states without hedging (e.g. "this is a ZZ plant", "Sansevieria trifasciata laurentii", "native to Madagascar"). Treat these as AUTHORITATIVE TRUTH for className / taxonomicFamily / genus / species / variety / nativeRegions. Do NOT override them with the image; use the image only to resolve fields the owner did not state.
2. HEDGED HINTS — uncertain phrasings ("I think", "maybe", "labeled as", "might be", "possibly", "the store said"). Treat these as SOFT EVIDENCE; cross-check against the image and diverge when the image clearly contradicts. Confidence should reflect the reconciliation.
```

## User template (active version 2)

```
Identify the plant in the attached image.
{{#if plant_name}}Owner's nickname or stated name for this plant (apply OWNER CERTAINTY — if this reads as an assertion about what the plant is, treat it as authoritative): {{plant_name}}{{/if}}
{{#if location}}Where the owner says the plant lives (may assert an indoor/outdoor context — honor assertions, treat hedges as soft hints): {{location}}{{/if}}

Return JSON matching the schema with className, taxonomicFamily, genus, species, variety, confidence, and nativeRegions. When the owner has asserted a species, cultivar, or native region, reflect that assertion in the corresponding field rather than overriding it from the photo.
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed alongside the other bio-section prompts. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule; drop the "weak hint / do not over-weight" framing on `plant_name` and `location`. |
