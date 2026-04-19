# DALL·E plant illustration

> Stylized watercolor-style illustration of a plant, used as decorative art in the plant UI. **Hardcoded** in Java — not stored in `llm_prompts`.

## Summary

- **Asks for:** a clean, elegant botanical illustration of the plant in soft watercolor style on a white background. No text or labels.
- **Returns:** raw PNG bytes (base64-decoded from the API response).

## Used by

- [`OpenAiImageGenerationClient.generateIllustratedPlantImage`](../../../backend/src/main/java/com/planted/client/OpenAiImageGenerationClient.java) — builds the prompt via `String.format` and calls `https://api.openai.com/v1/images/generations` (DALL·E 3 by default, configurable via `planted.openai.image-model`).
- Invoked from [`PlantIllustrationProcessor`](../../../backend/src/main/java/com/planted/worker/PlantIllustrationProcessor.java) at line 38 after a plant analysis is complete; the resulting PNG is stored via `ImageStorageService` and used as the plant's decorative illustration.

## Input variables

All are Java `String` arguments — not handlebars.

| Argument | Source | Example |
|---|---|---|
| `genus` | `Plant.getGenus()` (defaults to `"plant"` when null) | `"Dracaena"` |
| `species` | `Plant.getSpecies()` | `"trifasciata"` |
| `location` | `Plant.getLocation()` | `"living room"` |

The species name is rendered as `"{genus} {species}"` and the location is prefixed with `" in a "` when non-null.

## Output

- Raw `byte[]` of a 1024×1024 PNG. The API returns base64 JSON; the client decodes it into bytes.
- Request params: `n: 1`, `size: "1024x1024"`, `response_format: "b64_json"`.

## Prompt (hardcoded)

```java
String prompt = String.format(
    "A clean, elegant botanical illustration of a %s%s. " +
    "Soft watercolor style with natural green tones, white background, " +
    "premium botanical journal aesthetic. No text, no labels.",
    speciesName, locationContext
);
```

Fully rendered example (genus=Dracaena, species=trifasciata, location="living room"):

> A clean, elegant botanical illustration of a Dracaena trifasciata in a living room. Soft watercolor style with natural green tones, white background, premium botanical journal aesthetic. No text, no labels.

## Notes

- No `llm_prompts` row exists for this prompt. If you want to iterate on it via the normal DB-versioning flow, you'd need to add a row and change `OpenAiImageGenerationClient` to render from it — not done today.
- No audit row in `llm_requests` either; image generations are not tracked there. Plant illustrations are persisted as image rows on the plant.
