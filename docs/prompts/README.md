# Planted LLM Prompt Catalog

This is the canonical, human-readable index of every prompt Planted sends to an LLM. It is **not** the runtime source of truth — the runtime reads prompts from the `llm_prompts` Postgres table, seeded and updated by Flyway migrations under [`backend/src/main/resources/db/migration/`](../../backend/src/main/resources/db/migration). This catalog exists so engineers, reviewers, and prompt-tuners can see at a glance, for every prompt, what it asks the model to do, what variables get injected, what JSON it returns, and where in the app its output is rendered.

Keep it in sync. Whenever a prompt body, variable set, output schema, or call site changes, update the matching file in this directory **in the same change**. The `.cursor/rules/prompt-catalog.mdc` rule fires on the files that typically cause these changes and will remind you.

## How prompts work in Planted

- **Storage.** Each prompt is a pair of rows in `llm_prompts` — one for `role = 'system'`, one for `role = 'user'` — keyed by `(prompt_key, version, role)`. The table is created in [`V6__create_llm_prompts.sql`](../../backend/src/main/resources/db/migration/V6__create_llm_prompts.sql).
- **Versioning.** Each prompt has an integer `version` column and an `is_active` boolean. When we iterate, we insert a new version row and flip the previous version's `is_active` to false. The runtime always reads the **highest `version`** among `is_active = true` rows.
- **Loading.** [`OpenAiPlantClient.resolvePrompt` / `renderUserPrompt`](../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java) fetch that row and substitute variables.
- **Templating.** Custom handlebars-lite: `{{var_name}}` is literal substitution, `{{#if var_name}}…{{/if}}` drops a block when the variable is empty/blank. No nesting, no helpers, no escaping — be careful with curly braces in prompt bodies.
- **Output.** Every chat-completion call uses OpenAI structured outputs (`response_format: json_schema`). The schema is built in Java (in [`OpenAiPlantClient`](../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java) or [`BioSectionSchemas`](../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java)) and named by stripping the trailing `_vNN` from the `prompt_key` and appending `_response` (see `responseFormatSchemaName`).
- **Audit.** Every call persists a row to `llm_requests` with `prompt_key`, the rendered prompt, the input variables, and the raw response (see [`V7__create_llm_requests.sql`](../../backend/src/main/resources/db/migration/V7__create_llm_requests.sql)).
- **One exception.** The DALL·E illustration prompt is not in `llm_prompts` at all — it is a hardcoded `String.format` in [`OpenAiImageGenerationClient`](../../backend/src/main/java/com/planted/client/OpenAiImageGenerationClient.java). It is documented here anyway.

## Index

### Active prompts

| Prompt key | Purpose | Triggered from | Docs |
|---|---|---|---|
| `plant_registration_analysis_v1` | Full plant registration / reanalysis from a single photo. Identifies species and produces the whole care profile + health diagnosis + species overview. | [`PlantAnalysisProcessor`](../../backend/src/main/java/com/planted/worker/PlantAnalysisProcessor.java) | [registration/plant_registration_analysis_v1.md](registration/plant_registration_analysis_v1.md) |
| `pruning_analysis_v1` | Pruning-specific vision analysis on 1–3 photos. Returns conservative cut recommendations. | [`PlantPruningProcessor`](../../backend/src/main/java/com/planted/worker/PlantPruningProcessor.java) | [pruning/pruning_analysis_v1.md](pruning/pruning_analysis_v1.md) |
| `plant_history_summary_v2` | Per-calendar-day narrative digests of a plant's journal + care log. Powers the History section. | [`PlantHistorySummaryProcessor`](../../backend/src/main/java/com/planted/worker/PlantHistorySummaryProcessor.java) | [history/plant_history_summary_v2.md](history/plant_history_summary_v2.md) |
| `plant_placement_notes_summary_v1` | One-line caption paraphrasing the owner's placement notes for the plant-detail header. | [`PlacementNotesSummaryProcessor`](../../backend/src/main/java/com/planted/worker/PlacementNotesSummaryProcessor.java) (deprecated — superseded by `PLACEMENT_CARE` bio section) | [placement/plant_placement_notes_summary_v1.md](placement/plant_placement_notes_summary_v1.md) |
| `plant_species_id_v1` | Bio section: species identification from photo. | [`SpeciesIdStrategy`](../../backend/src/main/java/com/planted/bio/strategies/SpeciesIdStrategy.java) via [`PlantBioSectionProcessor`](../../backend/src/main/java/com/planted/worker/PlantBioSectionProcessor.java) | [bio/plant_species_id_v1.md](bio/plant_species_id_v1.md) |
| `plant_health_assessment_v1` | Bio section: realistic health read from photo. | [`HealthAssessmentStrategy`](../../backend/src/main/java/com/planted/bio/strategies/HealthAssessmentStrategy.java) | [bio/plant_health_assessment_v1.md](bio/plant_health_assessment_v1.md) |
| `plant_species_description_v1` | Bio section: encyclopedia-style prose about the species. Text-only. | [`SpeciesDescriptionStrategy`](../../backend/src/main/java/com/planted/bio/strategies/SpeciesDescriptionStrategy.java) | [bio/plant_species_description_v1.md](bio/plant_species_description_v1.md) |
| `plant_water_care_v1` | Bio section: watering amount + frequency + guidance. | [`WaterCareStrategy`](../../backend/src/main/java/com/planted/bio/strategies/WaterCareStrategy.java) | [bio/plant_water_care_v1.md](bio/plant_water_care_v1.md) |
| `plant_fertilizer_care_v1` | Bio section: fertilizer type + frequency + guidance. | [`FertilizerCareStrategy`](../../backend/src/main/java/com/planted/bio/strategies/FertilizerCareStrategy.java) | [bio/plant_fertilizer_care_v1.md](bio/plant_fertilizer_care_v1.md) |
| `plant_pruning_care_v1` | Bio section: species-level pruning guidance (no image). | [`PruningCareStrategy`](../../backend/src/main/java/com/planted/bio/strategies/PruningCareStrategy.java) | [bio/plant_pruning_care_v1.md](bio/plant_pruning_care_v1.md) |
| `plant_light_care_v1` | Bio section: light needs + guidance. | [`LightCareStrategy`](../../backend/src/main/java/com/planted/bio/strategies/LightCareStrategy.java) | [bio/plant_light_care_v1.md](bio/plant_light_care_v1.md) |
| `plant_placement_care_v1` | Bio section: placement recommendation tailored to owner's notes + geography. | [`PlacementCareStrategy`](../../backend/src/main/java/com/planted/bio/strategies/PlacementCareStrategy.java) | [bio/plant_placement_care_v1.md](bio/plant_placement_care_v1.md) |
| `plant_history_summary_bio_v1` | Bio section: compact one-paragraph history summary (distinct from `plant_history_summary_v2`). | [`HistorySummaryStrategy`](../../backend/src/main/java/com/planted/bio/strategies/HistorySummaryStrategy.java) | [history/plant_history_summary_bio_v1.md](history/plant_history_summary_bio_v1.md) |
| _DALL·E illustration_ | Stylized watercolor illustration of the plant for UI. Hardcoded, not in `llm_prompts`. | [`PlantIllustrationProcessor`](../../backend/src/main/java/com/planted/worker/PlantIllustrationProcessor.java) | [image/dalle_illustration.md](image/dalle_illustration.md) |

### Archived / unused

These rows exist in `llm_prompts` but have no live call sites in the Java code, or have been deactivated by a later migration. They are kept for historical context.

| Prompt key | Status | Docs |
|---|---|---|
| `plant_info_panel_v1` | Seeded in V6; no references in Java. | [archive/plant_info_panel_v1.md](archive/plant_info_panel_v1.md) |
| `plant_reminder_recompute_v1` | Seeded in V6; no references in Java. | [archive/plant_reminder_recompute_v1.md](archive/plant_reminder_recompute_v1.md) |
| `plant_history_summary_v1` | Deactivated in V12 (superseded by v2). | [archive/plant_history_summary_v1.md](archive/plant_history_summary_v1.md) |

## Keeping this catalog updated

When you do any of the following, update the matching doc in the same commit:

1. **Add or rev a prompt** — write/amend a Flyway migration that INSERTs or UPDATEs `llm_prompts`. Edit the matching `<area>/<prompt_key>.md` to reflect the new active body and add a line to _Version history_.
2. **Add or remove an injected variable** — update _Input variables_ and the verbatim user template. If the call site changed, update _Used by_.
3. **Add, remove, or rename a call site** in [`OpenAiPlantClient`](../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java), a worker, or a bio strategy. Update _Used by_.
4. **Change the JSON response schema** in [`OpenAiPlantClient`](../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java) or [`BioSectionSchemas`](../../backend/src/main/java/com/planted/bio/BioSectionSchemas.java). Update _Output schema_.
5. **Introduce a brand-new prompt_key** — create a new `<area>/<prompt_key>.md` using the template in an existing file, add it to the index above.
6. **Deprecate / deactivate a prompt** — move its doc to `archive/` and add a note explaining when and why it was retired.
