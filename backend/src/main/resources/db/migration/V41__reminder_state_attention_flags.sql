-- Extend plant_reminder_state with light / placement attention flags (and reason
-- strings for all three qualitative "needs attention" categories: health, light,
-- placement). health_attention_needed already exists on this table from V5 but
-- was never populated; starting in V41 the bio-section pipeline writes all three
-- flags so the plant list + screensaver icon row can illuminate them.
--
-- goal_attention_needed has no writer anywhere in the codebase; drop it from the
-- DTO/UI, but keep the column for now to avoid a breaking migration. The API
-- stops reading it.

ALTER TABLE plant_reminder_state
    ADD COLUMN light_attention_needed     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN placement_attention_needed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN health_attention_reason    TEXT,
    ADD COLUMN light_attention_reason     TEXT,
    ADD COLUMN placement_attention_reason TEXT;
