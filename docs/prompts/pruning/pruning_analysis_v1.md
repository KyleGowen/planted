# pruning_analysis_v1

> Conservative pruning advisor that reviews 1–3 plant photos and decides whether to prune, how much, and where. "No pruning required" is a valid and often preferred answer.

## Summary

- **Asks for:** given up to three images of one plant plus species context (genus, species, any species-level pruning guidance the app already has), owner goals, recent care history, and owner address, return JSON recommending whether and how much to prune, with specific recommendations tied to owner goals. Only recommend pruning when clear evidence of dead / damaged / crowded / goal-impeding growth is visible. Avoid aggressive advice.
- **Returns:** a JSON object — `pruningNeeded` (bool), `verdict`, `pruningAmount`, `specificRecommendations[]`, `goalAlignment`, `confidence`, `notes`.

## Used by

- [`PlantPruningProcessor.process`](../../../backend/src/main/java/com/planted/worker/PlantPruningProcessor.java) at line 91 — triggered when a plant is re-analyzed specifically for pruning (pruning flow, typically after the user submits 1–3 new angle photos).
- Calls [`OpenAiPlantClient.analyzePruning`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- The result populates fields on `plant_analyses` and surfaces in the pruning UI.

## Input variables

| Variable | Required | Source | Example |
|---|---|---|---|
| `genus` | yes | `Plant.genus` (defaulted to `"Unknown"`) | `"Dracaena"` |
| `species` | yes | `Plant.species` | `"trifasciata"` |
| `image_count` | yes | size of the image list passed by `PlantPruningProcessor` | `"3"` |
| `goals_text` | no | `Plant.getGoalsText()` | `"Keep it compact"` |
| `pruning_guidance` | no | species-level pruning guidance text already stored on the plant | multi-sentence species reference |
| `care_history` | no | [`CareHistoryFormatter.formatForLlm`](../../../backend/src/main/java/com/planted/service/CareHistoryFormatter.java) | `"Last pruned 2024-02-01"` |
| `history_notes` | no | recent owner journal notes (rarely used on this prompt) | multiline |
| `owner_physical_address` | no | [`UserPhysicalAddressService`](../../../backend/src/main/java/com/planted/service/UserPhysicalAddressService.java) | `"1234 Main St, Austin, TX"` |

Images are passed via `image_url` content blocks (1–3 photos of the same plant from different angles).

## Output schema

- `response_format` name: `pruning_analysis_response`.
- Java model: [`PruningAnalysisSchema`](../../../backend/src/main/java/com/planted/client/PruningAnalysisSchema.java).
- Schema builder: [`OpenAiPlantClient.pruningSchema`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).

Required fields:

```
pruningNeeded (bool), verdict (string), pruningAmount (string),
specificRecommendations (string[]), goalAlignment (string),
confidence (string), notes (string)
```

## System prompt (active version 4)

```
You are a conservative plant pruning advisor. Review the provided plant images and give pruning guidance. Important: "No pruning required" is a valid and preferred answer when the plant does not clearly need pruning. Only recommend pruning when there is clear evidence of dead, damaged, crowded, or goal-impeding growth. Avoid aggressive pruning recommendations. Tie suggestions to the user's stated goals when possible. When care history is provided, consider recent care events in your pruning assessment. When the owner's physical address is provided, you may use typical regional climate and seasonality only as background—do not invent live weather or forecasts.

OWNER CERTAINTY — the owner's free-text (goals_text, history_notes) is a tiered source of evidence:
1. ASSERTIONS — factual-sounding claims the owner states without hedging (e.g. "keep it compact", "I want more branching", "I pruned hard last month"). Treat these as AUTHORITATIVE TRUTH; tailor verdict / pruningAmount / specificRecommendations / goalAlignment to the asserted goals and asserted recent care history. Do not override an asserted goal with generic conservative advice.
2. HEDGED HINTS — uncertain phrasings ("I think", "maybe", "possibly"). Treat these as SOFT EVIDENCE you may cross-check against the images.
```

## User template (active version 4)

```
Plant: {{genus}} {{species}}.{{#if goals_text}}
User goals (apply OWNER CERTAINTY — asserted goals are authoritative): {{goals_text}}{{/if}}{{#if pruning_guidance}}
Species pruning guidance: {{pruning_guidance}}{{/if}}{{#if care_history}}
Recent care history: {{care_history}}{{/if}}{{#if history_notes}}
Owner observations and notes (apply OWNER CERTAINTY — asserted facts are authoritative):
{{history_notes}}{{/if}}{{#if owner_physical_address}}
Owner's physical address (climate context): {{owner_physical_address}}. Use typical regional climate only; do not claim live weather.{{/if}}
Please review the attached images ({{image_count}} photo(s) from different angles) and provide conservative pruning recommendations. Return structured JSON.
```

## Version history

Active version: **4**.

| Migration | Version | Change |
|---|---|---|
| [V6](../../../backend/src/main/resources/db/migration/V6__create_llm_prompts.sql) | 1 | Initial seed. |
| [V10](../../../backend/src/main/resources/db/migration/V10__update_prompts_with_care_context.sql) | 2 | Consider recent care history. |
| [V21](../../../backend/src/main/resources/db/migration/V21__owner_physical_address_prompts.sql) | 3 | Add `owner_physical_address` and `history_notes` variables. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | 4 | Add OWNER CERTAINTY rule — asserted goals and asserted recent care facts drive the recommendation rather than being overridden by a generic conservative default. |

## Notes

- The bio-section prompt [`plant_pruning_care_v1`](../bio/plant_pruning_care_v1.md) produces _species-level_ pruning text (no image) and is independent of this prompt; this prompt is the image-driven per-plant recommendation.
- Image handling path lives in [`OpenAiPlantClient.callWithImages`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
