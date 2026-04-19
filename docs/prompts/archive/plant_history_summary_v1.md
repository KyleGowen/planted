# plant_history_summary_v1 (archived)

> First version of the plant history summary. Deactivated in [V12](../../../backend/src/main/resources/db/migration/V12__history_summary_rich_context.sql) when `plant_history_summary_v2` took over with a richer profile + sectioned output.

## Superseded by

[`plant_history_summary_v2`](../history/plant_history_summary_v2.md) — which eventually became the daily-digests prompt documented in that file.

## Why retired

- v1 produced a single narrative blob; v2 layered in profile + goals and emitted sectioned text.
- Subsequent migrations (V17, V21, V25–V27) further refined v2 into the current daily-digest structured output.

## Notes

- Rows for v1 remain in `llm_prompts` with `is_active = false` for audit history.
- No call site in the Java code references this `prompt_key` anymore.
