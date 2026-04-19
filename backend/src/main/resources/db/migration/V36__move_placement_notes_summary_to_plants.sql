-- Move placement_notes_summary off plant_analyses and onto plants. The summary is a
-- short one-sentence caption derived directly from plants.location and is produced by
-- a dedicated small LLM call (plant_placement_notes_summary_v1), not the full
-- registration/reanalysis schema. Deactivate registration prompt v14 (which added the
-- inline placementNotesSummary output) and restore v13 so the main analysis no longer
-- produces that field.

ALTER TABLE plants
    ADD COLUMN placement_notes_summary TEXT;

ALTER TABLE plant_analyses
    DROP COLUMN IF EXISTS placement_notes_summary;

UPDATE llm_prompts
SET is_active = FALSE, updated_at = NOW()
WHERE prompt_key = 'plant_registration_analysis_v1'
  AND version = 14;

UPDATE llm_prompts
SET is_active = TRUE, updated_at = NOW()
WHERE prompt_key = 'plant_registration_analysis_v1'
  AND version = 13;
