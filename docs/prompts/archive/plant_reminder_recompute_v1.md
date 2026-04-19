# plant_reminder_recompute_v1 (archived)

> Seeded in [V6](../../../backend/src/main/resources/db/migration/V6__create_llm_prompts.sql) but **has no live call site in the Java code**. Reminder recomputation is now a deterministic service that does not invoke an LLM.

## Intended purpose

Have an LLM decide whether watering / fertilizing / pruning is due for a plant based on its species care requirements, last-event timestamps, weather context, and time of year. Superseded by rule-based logic in [`PlantReminderService`](../../../backend/src/main/java/com/planted/service/PlantReminderService.java) (and [`PlantReminderRecomputeProcessor`](../../../backend/src/main/java/com/planted/worker/PlantReminderRecomputeProcessor.java)), which compares the plant's last care event to a frequency string and fires reminders without a model call.

## System prompt (seed)

```
You are a plant care scheduling assistant. Based on the plant species care requirements and recent care history, determine whether watering, fertilizing, or pruning is due. Consider time of year and any provided weather context. Return specific, actionable next-step instructions. Be practical and concise.
```

## User template (seed)

```
Plant: {{genus}} {{species}}. Watering frequency: {{watering_frequency}}. Last watered: {{last_watered_at}}. Last fertilized: {{last_fertilized_at}}. Last pruned: {{last_pruned_at}}. Current date: {{current_date}}. {{#if weather_context}}Weather: {{weather_context}}{{/if}} Determine if watering, fertilizing, or pruning is due and provide next-step instructions.
```

## Notes

- Safe to deactivate or drop in a future migration.
