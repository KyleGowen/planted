# plant_water_care_v1

> Bio section: short, practical watering guidance for one plant given species + growing context + placement notes + geography. Text-only.

## Summary

- **Asks for:** produce conservative watering guidance. Err toward less water for typical houseplants. Reflect local climate when geography is provided. Do not invent numeric schedules when the species is not well-characterized.
- **Returns:** `{ amount, frequency, guidance }` — short phrases for the first two, 2–4 sentences for guidance distinguishing under- vs overwatering cues.

## Used by

- [`WaterCareStrategy`](../../../backend/src/main/java/com/planted/bio/strategies/WaterCareStrategy.java) via [`PlantBioSectionProcessor`](../../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) → [`OpenAiPlantClient.generateBioSection`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).

## Input variables

| Variable | Required | Source |
|---|---|---|
| `species_name` | yes | [`BioSectionContext.speciesName`](../../../backend/src/main/java/com/planted/bio/BioSectionContext.java) |
| `growing_context` | no | `Plant.getGrowingContext().name()` (enum, e.g. `INDOOR`, `OUTDOOR`) |
| `location` | no | `Plant.getLocation()` — placement notes; owner-asserted placement is authoritative (OWNER CERTAINTY). |
| `geographic_location` | no | `BioSectionContext.geographicLocation` |
| `goals_text` | no | `BioSectionContext.goalsText` → `Plant.getGoalsText()` — owner-asserted watering routines / care practices are authoritative (OWNER CERTAINTY). |

## Output schema

- `response_format` name: `plant_water_care_response`.
- Schema: [`BioSectionSchemas.waterCare`](../../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java).

```
{ amount: string, frequency: string, guidance: string }
```

## System prompt (active version 2)

```
You produce short, practical watering guidance for a single plant. Be conservative: err toward less water for typical houseplants. When geography is provided, reflect local climate (dry/humid, hot/cold seasons). Do not invent specific numeric schedules when the species is not well-characterized.

OWNER CERTAINTY — the owner's notes (goals_text, location) are a tiered source of evidence:
1. ASSERTIONS — factual-sounding claims the owner states without hedging (e.g. "I water every 3 days and it's thriving", "I let it go bone dry between waterings", "the pot has no drainage"). Treat these as AUTHORITATIVE TRUTH about what is currently working for this plant. Do NOT contradict a working asserted routine with a generic conservative schedule; tailor amount / frequency / guidance to reinforce it, and only caution if the owner describes a visible problem themselves.
2. HEDGED HINTS — uncertain phrasings ("I think", "maybe", "possibly", "the tag said"). Treat these as SOFT EVIDENCE you may cross-check against the species and climate.
```

## User template (active version 2)

```
Species: {{species_name}}
{{#if growing_context}}Growing context: {{growing_context}}{{/if}}
{{#if location}}Placement notes (apply OWNER CERTAINTY — asserted placement facts are authoritative): {{location}}{{/if}}
{{#if geographic_location}}Geographic location: {{geographic_location}}{{/if}}
{{#if goals_text}}Owner notes and any claims about watering routine or plant behaviour (apply OWNER CERTAINTY — owner-asserted facts are authoritative; do not override a working asserted routine with a generic conservative schedule): {{goals_text}}{{/if}}

Return JSON with:
- amount: short phrase (e.g. "Water until it drains from the bottom").
- frequency: short phrase (e.g. "Every 7–10 days when the top inch is dry"). If the owner has asserted a working cadence, this field must be consistent with it.
- guidance: 2–4 sentences explaining how to know when to water this plant in its context, distinguishing under- vs overwatering cues, and folding in any owner-asserted routine rather than contradicting it.
```

## Version history

| Migration | Change |
|---|---|
| [V39](../../../backend/src/main/resources/db/migration/V39__bio_section_prompts_v1.sql) | Initial seed. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule and thread `goals_text` through so owner-asserted watering routines are honored instead of overridden by a generic conservative schedule. |
