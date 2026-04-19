-- Adds a dedicated column for the LLM's short paraphrase of the owner's placement
-- notes (plants.location). Shown as a third line under Indoor/Outdoor in the plant
-- detail header; distinct from placement_guidance (advice) and
-- placement_general_guidance (educational).

ALTER TABLE plant_analyses
    ADD COLUMN placement_notes_summary TEXT;
