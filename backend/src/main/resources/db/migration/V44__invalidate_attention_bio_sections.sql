-- Lazy backfill for V42's new attention prompts. The fingerprint cache in
-- plant_bio_sections is a SHA-256 of the strategy's template inputs — it does
-- NOT include the prompt version — so simply bumping plant_light_care_v1 /
-- plant_placement_care_v1 / plant_health_assessment_v1 to a new version in
-- llm_prompts leaves cached rows considered "fresh" until their 24h TTL rolls.
--
-- To pick up the new attentionNeeded + attentionReason fields immediately, zero
-- out generated_at and inputs_fingerprint on the three attention-bearing
-- sections for every plant. The reader (PlantBioSectionProcessor) treats
-- generated_at IS NULL as stale; the next time each plant is viewed (or a care
-- event triggers a refresh) its attention sections will be re-generated under
-- the new prompt version, and PlantReminderService.syncBioAttention will fan
-- the flags onto plant_reminder_state.
--
-- This matches the BioSectionInvalidator pattern (zero out, do not enqueue) so
-- we don't spend tokens on plants nobody is looking at.

UPDATE plant_bio_sections
SET generated_at = NULL,
    inputs_fingerprint = NULL,
    updated_at = NOW()
WHERE section_key IN ('HEALTH_ASSESSMENT', 'LIGHT_CARE', 'PLACEMENT_CARE');
