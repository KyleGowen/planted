# plant_info_panel_v1 (archived)

> Seeded in [V6](../../../backend/src/main/resources/db/migration/V6__create_llm_prompts.sql) but **has no live call site in the Java code** and has never been deactivated. Effectively dead weight in `llm_prompts`.

## Intended purpose

Produce an informative species profile (`genus`, `species`, optional `variety`) for an "info panel" in the UI — native regions, history, interesting facts, uses. This role has since been covered by [`plant_species_description_v1`](../bio/plant_species_description_v1.md) (encyclopedia overview) + `nativeRegions` / `speciesOverview` on the full registration response.

## System prompt (seed)

```
You are a knowledgeable botanist. Provide engaging, accurate information about the given plant species. Focus on: native regions and natural habitat, interesting historical or cultural context, fascinating biological facts, and practical or culinary uses if clearly appropriate and safe. Keep responses informative but accessible.
```

## User template (seed)

```
Provide an informative species profile for {{genus}} {{species}}{{#if variety}} ({{variety}}){{/if}}. Return structured JSON with native_regions, history, interesting_facts (array), and uses (array, only if clearly safe and relevant).
```

## Notes

- No schema builder in Java; no `*_response` schema name.
- Safe to deactivate or drop in a future migration. Leaving a row in place carries no runtime cost since nothing calls it, but it is confusing to readers — prefer explicit deactivation if we're sure nothing references it.
