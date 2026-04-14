-- Paired care fields: primary line + general/educational line (light, placement, pruning).

ALTER TABLE plant_analyses
    ADD COLUMN light_general_guidance TEXT,
    ADD COLUMN placement_general_guidance TEXT,
    ADD COLUMN pruning_action_summary TEXT,
    ADD COLUMN pruning_general_guidance TEXT;
