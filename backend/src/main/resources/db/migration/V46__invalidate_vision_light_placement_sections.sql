-- Lazy backfill for V45's vision-aware LIGHT_CARE / PLACEMENT_CARE prompts
-- plus the reinforced HEALTH_ASSESSMENT. The fingerprint cache in
-- plant_bio_sections is a SHA-256 of the strategy's template inputs — it does
-- NOT include the prompt version — so simply bumping plant_light_care_v1,
-- plant_placement_care_v1, and plant_health_assessment_v1 in llm_prompts
-- leaves cached rows considered "fresh" until their 24h TTL rolls.
--
-- Additionally, LIGHT_CARE and PLACEMENT_CARE have just flipped from
-- text-only to vision-aware (PlantBioSectionKey.requiresImage=true), so their
-- old fingerprints no longer include the primary image id. Zero out
-- generated_at and inputs_fingerprint for the three attention-bearing
-- sections so the next view / refresh re-generates them under the new
-- prompts with the image attached, and PlantReminderService.syncBioAttention
-- fans the flags onto plant_reminder_state.
--
-- This matches the BioSectionInvalidator pattern (zero out, do not enqueue)
-- so we don't spend tokens on plants nobody is looking at.

UPDATE plant_bio_sections
SET generated_at = NULL,
    inputs_fingerprint = NULL,
    updated_at = NOW()
WHERE section_key IN ('HEALTH_ASSESSMENT', 'LIGHT_CARE', 'PLACEMENT_CARE');
