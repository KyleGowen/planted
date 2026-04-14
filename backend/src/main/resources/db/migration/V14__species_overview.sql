-- Narrative species profile (encyclopedia-style prose) from registration/reanalysis.
-- Legacy interesting_facts_json remains for read fallback until analyses are re-run.
ALTER TABLE plant_analyses
    ADD COLUMN species_overview TEXT;
