# plant_history_summary_v2

> Per-calendar-day narrative digests of a plant's journal + care log. Powers the History section on the plant detail page.

## Summary

- **Asks for:** given a grouped-by-day history block + detailed chronological log (+ optional attached baseline/journal photos, plant profile, and owner address), produce one short prose digest for every calendar day that has any activity. Reflect care counts accurately. Do NOT invent events or reasons. Do not infer why the owner performed a care action from timestamps alone. Flag biologically implausible same-day care counts (multiple waterings, repeated prunes).
- **Returns:** a JSON object `{ "daily_digests": [{ "day": "YYYY-MM-DD", "digest": "…" }, …] }` sorted newest day first.

## Used by

- [`PlantHistorySummaryProcessor.process`](../../../backend/src/main/java/com/planted/worker/PlantHistorySummaryProcessor.java) at line 130 — builds the timeline text via [`PlantHistorySummaryTimelineBuilder`](../../../backend/src/main/java/com/planted/worker/PlantHistorySummaryTimelineBuilder.java) and profile via [`PlantHistorySummaryProfileBuilder`](../../../backend/src/main/java/com/planted/worker/PlantHistorySummaryProfileBuilder.java), then calls [`OpenAiPlantClient.summarizePlantHistory`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- The response powers the History section rendered on the plant detail page ([`frontend/src/app/plants/[id]/page.tsx`](../../../frontend/src/app/plants/[id]/page.tsx)).

## Input variables

| Variable | Required | Source | Notes |
|---|---|---|---|
| `timeline_text` | yes | [`PlantHistorySummaryTimelineBuilder`](../../../backend/src/main/java/com/planted/worker/PlantHistorySummaryTimelineBuilder.java) and [`PlantHistorySummaryDayGroupBuilder`](../../../backend/src/main/java/com/planted/worker/PlantHistorySummaryDayGroupBuilder.java) | Two blocks: grouped-by-day with explicit care counts, then chronological detail. |
| `plant_profile` | no | [`PlantHistorySummaryProfileBuilder`](../../../backend/src/main/java/com/planted/worker/PlantHistorySummaryProfileBuilder.java) | Placement + goals + latest care snapshot — for disambiguation only. |
| `baseline_photo_note` | no | `PlantHistorySummaryProcessor` | Text note used when a baseline image could not be attached (e.g. S3 fetch failed). |
| `plant_name` | no | `Plant.getName()` | Owner's nickname. |
| `species_label` | no | display form of `Plant.scientificName` | `"Dracaena trifasciata"`. |
| `image_count` | no | formatted label | e.g. `"3 baseline + 2 journal"`. |
| `owner_physical_address` | no | [`UserPhysicalAddressService`](../../../backend/src/main/java/com/planted/service/UserPhysicalAddressService.java) | Typical climate context only. |

Images (baseline first, then journal photos) are attached via `image_url` blocks.

## Output schema

- `response_format` name: `plant_history_summary_response`.
- Java model: [`PlantHistorySummarySchema`](../../../backend/src/main/java/com/planted/client/PlantHistorySummarySchema.java).
- Schema builder: [`OpenAiPlantClient.historySummarySchema`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).

```
{
  daily_digests: Array<{
    day: string,    // YYYY-MM-DD
    digest: string  // 1–4 sentences of flowing prose
  }>
}
```

`PlantHistorySummarySchema.flattenForStorage` concatenates digests for persistence; the Java layer throws if `daily_digests` is empty or every digest is blank.

## System prompt (active version 1, latest content from V43)

```
You are a careful assistant producing short daily digests for a single houseplant's documented history for the owner's History section.

Output shape (enforced by the API schema): a JSON object with field daily_digests only — an array of objects, each with "day" (ISO local date YYYY-MM-DD) and "digest" (plain text). Include one digest for every calendar day that has any journal or care activity shown in the user message. Sort daily_digests with the newest calendar day first (descending by day string).

Rules:
- Use ONLY facts from the grouped-by-day section and the detailed chronological log in the user message, plus attached images when present. Do not invent events, dates, symptoms, or care actions.
- For each day, write 1–4 sentences of flowing prose (not a bullet list of separate events). Merge care and journal information naturally.
- When the grouped section gives explicit counts for watering, fertilizer, or prune for that day, reflect those counts accurately (e.g. how many times watered). If the count is 1, you may say "once" instead of a number.
- Reference journal notes and [photo attached] lines when present; when images are attached and relevant, you may add brief, conservative plant-condition observations. If an image is unclear, say so briefly.
- The plant profile block in the user message is for disambiguation only. Do NOT copy generic care guidance from it into the digests unless the owner's log explicitly mentions that topic.
- When an owner's physical address is provided, you may use typical regional climate only as background to interpret the log; do not add digest content from climate alone, and do not invent current or forecast weather.
- If the log is very thin, keep digests short and factual. Do not pad with filler.
- Do not repeat the same calendar day twice in daily_digests.

OWNER CERTAINTY — when the owner's journal notes assert a fact about their plant (species, cultivar, origin, native range, the room or light it lives in, a care routine that is working), treat that assertion as AUTHORITATIVE TRUTH for that day's digest. Do not soften, hedge, or contradict an asserted fact with generic species knowledge. Only hedged owner phrasings ("I think", "maybe", "not sure") should themselves be reported with hedging.

Intent and purpose (critical):
- Do NOT infer or state why the owner performed a care action (watering, fertilizer, pruning) from timestamps or action types alone. Phrases like "these actions aimed to…", "in order to support…", "to promote healthy growth", "seeking to balance…", or similar motivational framing are forbidden unless that exact idea appears in the owner's own note text (journal entry or notes field on a care event).
- Describe what was recorded: counts, that an event occurred, and any text the owner actually wrote. If there are no owner notes for a day, stick to neutral factual description of events only—no implied goals or reasons.
- You may summarize visible condition from photos or from owner-stated observations in the log; that is not the same as inferring why they watered or pruned.

Tone and care realism (critical — not sycophantic):
- Do NOT praise, flatter, or reassure the owner based on action volume. Avoid language that implies many same-day events are "good", "thorough", "consistent care", "supports ongoing health", "likely improves balance", or similar unless the owner's own notes explicitly say that.
- When same-day counts are unusually high for typical indoor potted-plant care (for example: multiple full waterings, multiple fertilizer applications, or many prune events in one calendar day), you MUST briefly flag that the pattern is atypical and could stress the plant (overwatering, nutrient overload, or repeated pruning stress) or could reflect logging/testing errors. Keep wording calm and factual—no scolding, no moralizing.
- If owner notes or images suggest problems (yellowing, wilting, burn) on a day with heavy care, you may conservatively connect only what the log states; do not invent symptoms.
- Prefer neutral clinical phrasing: state what was logged, then one short caution when counts alone warrant it.
```

## User template (active version 1, latest content from V27)

```
{{#if plant_name}}Plant nickname: {{plant_name}}
{{/if}}{{#if species_label}}Species (if known): {{species_label}}
{{/if}}{{#if plant_profile}}=== Plant profile (context only; do not paste into digests) ===
{{plant_profile}}

{{/if}}{{#if baseline_photo_note}}Note: {{baseline_photo_note}}

{{/if}}{{#if owner_physical_address}}Owner's physical address (typical climate context only, not live weather): {{owner_physical_address}}

{{/if}}Below is the owner's history. The first block groups events by local calendar day with explicit care counts per day; the detailed chronological section repeats the same facts. Build daily_digests from these sources only. Do not guess the owner's reasons for care actions unless they wrote those reasons in a note. When same-day care counts look biologically heavy or implausible for a typical houseplant, say so plainly in the digest (risk of overwatering, fertilizer burn, or repeated stress)—do not praise high frequency as healthy by default.
{{#if image_count}}Attached images: {{image_count}}.
{{/if}}

{{timeline_text}}
```

## Version history

Active version: **1** (the `_v2` suffix is in the `prompt_key` itself; the `version` integer has stayed at 1 and been UPDATEd in place).

| Migration | Change |
|---|---|
| [V12](../../../backend/src/main/resources/db/migration/V12__history_summary_rich_context.sql) | Introduce `plant_history_summary_v2` with profile + sectioned prose output. |
| [V17](../../../backend/src/main/resources/db/migration/V17__plant_history_summary_timeline_tone.sql) | Tone + pure-timeline formatting update. |
| [V21](../../../backend/src/main/resources/db/migration/V21__owner_physical_address_prompts.sql) | Add `owner_physical_address` as climate context. |
| [V25](../../../backend/src/main/resources/db/migration/V25__history_summary_daily_digests_prompt.sql) | Switch output to structured `daily_digests`. |
| [V26](../../../backend/src/main/resources/db/migration/V26__history_summary_no_inferred_intent.sql) | Forbid inferring owner intent from care actions alone. |
| [V27](../../../backend/src/main/resources/db/migration/V27__history_summary_conservative_care_tone.sql) | No sycophantic praise; flag implausibly heavy same-day care. |
| [V43](../../../backend/src/main/resources/db/migration/V43__owner_certainty_prompts.sql) | Add OWNER CERTAINTY rule — owner-asserted facts in journal notes are authoritative and must not be softened or contradicted in the digest. |

## Notes

- `plant_history_summary_v1` (separate key) was deactivated in V12 and is archived — see [archive/plant_history_summary_v1.md](../archive/plant_history_summary_v1.md).
- The bio-section variant [`plant_history_summary_bio_v1`](plant_history_summary_bio_v1.md) produces a compact **single** paragraph for the Bio page and is served from a separate cache. This `_v2` prompt stays the source of the full daily-digest narrative.
