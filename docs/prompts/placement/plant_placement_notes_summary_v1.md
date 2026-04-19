# plant_placement_notes_summary_v1

> Compresses an owner's free-text placement notes (`plants.location`) into a single-sentence caption shown as the third line under the Indoor/Outdoor label on the plant detail page.

## Summary

- **Asks for:** paraphrase the owner's placement notes into one line under ~100 characters (never over 120). Keep the single most useful placement fact (room, window direction, light, indoor/outdoor, balcony, shelf). Drop filler. Correct obvious typos. No advice, no species context, no climate or geography, no evaluative language. Do not invent details. Return an empty string when the notes are missing or unusable.
- **Returns:** `{ "summary": string }` — one short line, or `""` when unusable.

## Used by

- [`PlacementNotesSummaryProcessor.process`](../../../backend/src/main/java/com/planted/worker/PlacementNotesSummaryProcessor.java) at line 52, calling [`OpenAiPlantClient.summarizePlacementNotes`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).
- The summary is persisted to `plants.placement_notes_summary` (introduced in [V36](../../../backend/src/main/resources/db/migration/V36__move_placement_notes_summary_to_plants.sql)) and rendered under Indoor/Outdoor in the plant detail header.

**Deprecation note.** `PlacementNotesSummaryProcessor` is marked `@Deprecated(forRemoval = true)` — the [`PLACEMENT_CARE` bio section](../bio/plant_placement_care_v1.md) now produces richer placement guidance. This small summary prompt stays wired up until the legacy `plants.placement_notes_summary` column has no readers.

## Input variables

| Variable | Required | Source |
|---|---|---|
| `location` | yes (but may be blank) | [`Plant.getLocation()`](../../../backend/src/main/java/com/planted/entity/Plant.java) |

No image. Pure text-only chat completion.

## Output schema

- `response_format` name: `plant_placement_notes_summary_response`.
- Schema builder: [`OpenAiPlantClient.placementNotesSummarySchema`](../../../backend/src/main/java/com/planted/client/OpenAiPlantClient.java).

```
{ summary: string }  // <= ~120 chars, or "" when unusable
```

## System prompt (active version 1)

```
You rewrite an owner's free-text notes about where their plant lives into a single short caption for a mobile UI.

Rules for the "summary" output:
- Exactly one line, one sentence. No line breaks, no bullet points, no list markers, no quotation marks.
- Aim for well under 100 characters; never exceed 120.
- Paraphrase what the owner wrote — do NOT copy it verbatim. Keep the most useful placement fact (room, window direction, light, indoor/outdoor, balcony, shelf, etc.) and drop filler. Leaving information out is fine and usually expected.
- Correct obvious typos. Use neutral, descriptive phrasing; title-ish sentence case is fine, e.g. "Living room east window with morning sun" or "West-facing balcony, afternoon shade".
- Do NOT add any advice, recommendations, species context, climate, geography, owner name, or evaluative language ("great spot", "too dark", "perfect light").
- Do NOT invent any detail that is not present or directly implied by the owner's notes (no made-up window direction, floor level, light duration, or building material).
- If the owner's notes are missing, blank, or contain no usable placement information, return an empty string for "summary" — do not fabricate a location.
```

## User template (active version 1)

```
Owner's placement notes:
{{location}}

Return JSON matching the schema. Put your single-sentence paraphrase in "summary". If the notes are blank or unusable, return an empty string.
```

## Version history

| Migration | Change |
|---|---|
| [V37](../../../backend/src/main/resources/db/migration/V37__placement_notes_summary_prompt_v1.sql) | Initial seed after the summary was moved off registration v14 and onto a dedicated call. |

## Notes

- Related but distinct: the registration prompt was briefly expanded (V35, version 14) to emit this summary inline. [V36](../../../backend/src/main/resources/db/migration/V36__move_placement_notes_summary_to_plants.sql) rolled that back and moved the feature to this dedicated prompt + its own table column.
