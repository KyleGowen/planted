-- Outdoor growing context + coordinates for weather-aware reminders; optional weather note on reminder state.

ALTER TABLE plants
    ADD COLUMN growing_context VARCHAR(32) NOT NULL DEFAULT 'INDOOR',
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;

ALTER TABLE plant_reminder_state
    ADD COLUMN weather_care_note TEXT;
